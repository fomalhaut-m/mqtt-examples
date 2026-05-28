package top.vexruna.simulator.test;

import org.junit.jupiter.api.*;
import top.vexruna.simulator.queue.RingBuffer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    @DisplayName("基础写入和读取")
    void testBasicAddAndGet() {
        RingBuffer<String> buffer = new RingBuffer<>(3);
        buffer.add("a");
        buffer.add("b");
        buffer.add("c");

        assertEquals(3, buffer.size());
        assertEquals("a", buffer.get(0));
        assertEquals("b", buffer.get(1));
        assertEquals("c", buffer.get(2));
    }

    @Test
    @DisplayName("覆盖写入 - 超过容量后覆盖最旧数据")
    void testOverwrite() {
        RingBuffer<String> buffer = new RingBuffer<>(3);
        buffer.add("a");
        buffer.add("b");
        buffer.add("c");
        buffer.add("d");

        assertEquals(3, buffer.size());
        assertEquals("b", buffer.get(0));
        assertEquals("c", buffer.get(1));
        assertEquals("d", buffer.get(2));
    }

    @Test
    @DisplayName("toList 顺序正确")
    void testToList() {
        RingBuffer<Integer> buffer = new RingBuffer<>(5);
        for (int i = 0; i < 8; i++) {
            buffer.add(i);
        }

        List<Integer> list = buffer.toList();
        assertEquals(5, list.size());
        assertEquals(List.of(3, 4, 5, 6, 7), list);
    }

    @Test
    @DisplayName("isFull 判断")
    void testIsFull() {
        RingBuffer<String> buffer = new RingBuffer<>(2);
        assertFalse(buffer.isFull());
        buffer.add("a");
        assertFalse(buffer.isFull());
        buffer.add("b");
        assertTrue(buffer.isFull());
    }

    @Test
    @DisplayName("clear 清空")
    void testClear() {
        RingBuffer<String> buffer = new RingBuffer<>(3);
        buffer.add("a");
        buffer.add("b");
        buffer.clear();
        assertEquals(0, buffer.size());
        assertFalse(buffer.isFull());
    }

    @Test
    @DisplayName("get 越界抛异常")
    void testGetOutOfBounds() {
        RingBuffer<String> buffer = new RingBuffer<>(3);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0));
        buffer.add("a");
        assertDoesNotThrow(() -> buffer.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(1));
    }

    @Test
    @DisplayName("容量为 0 抛异常")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(-1));
    }

    @Test
    @DisplayName("并发写入安全")
    void testConcurrentAccess() throws InterruptedException {
        RingBuffer<Integer> buffer = new RingBuffer<>(100);
        int threadCount = 8;
        int itemsPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int offset = t * itemsPerThread;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        buffer.add(offset + i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(buffer.size() <= 100);
        List<Integer> list = buffer.toList();
        assertEquals(100, list.size());
    }
}
