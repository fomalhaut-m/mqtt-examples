/**
 * 线程安全的环形队列（Ring Buffer）
 *
 * 什么是环形队列：
 *   - 固定容量的数组，写满后从头开始覆盖最旧的数据
 *   - 像一个首尾相连的环，新数据不断覆盖旧数据
 *
 * 为什么不用 ArrayList：
 *   - ArrayList 会无限增长，数据量大时 OOM（内存溢出）
 *   - RingBuffer 固定容量，内存使用可控
 *
 * 线程安全策略：
 *   - 使用 ReentrantReadWriteLock（读写锁）
 *   - 读操作（get/toList/size）用读锁：多个线程可以同时读，互不阻塞
 *   - 写操作（add/clear）用写锁：写的时候不能读也不能写，保证数据一致性
 *
 * 使用场景：
 *   - DeviceDataService 用它缓存最近 200 条设备数据
 *   - DeadLetterService 用它缓存最近 100 条死信消息
 */
package top.vexruna.simulator.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RingBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int head = 0;
    private int size = 0;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    public void add(T item) {
        lock.writeLock().lock();
        try {
            buffer[head] = item;
            head = (head + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
            }
            int actualIndex = (head - size + index + capacity) % capacity;
            return (T) buffer[actualIndex];
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> toList() {
        lock.readLock().lock();
        try {
            List<T> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int actualIndex = (head - size + i + capacity) % capacity;
                @SuppressWarnings("unchecked")
                T item = (T) buffer[actualIndex];
                result.add(item);
            }
            return Collections.unmodifiableList(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFull() {
        lock.readLock().lock();
        try {
            return size == capacity;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < capacity; i++) {
                buffer[i] = null;
            }
            head = 0;
            size = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
