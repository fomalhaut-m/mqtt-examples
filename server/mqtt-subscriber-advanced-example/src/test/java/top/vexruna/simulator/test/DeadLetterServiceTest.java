package top.vexruna.simulator.test;

import org.junit.jupiter.api.*;
import top.vexruna.simulator.dto.DeadLetterMessage;
import top.vexruna.simulator.mqtt.DeadLetterService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeadLetterServiceTest {

    private DeadLetterService deadLetterService;

    @BeforeEach
    void setUp() {
        deadLetterService = new DeadLetterService();
    }

    @Test
    @DisplayName("死信记录入队")
    void testCapture() {
        deadLetterService.capture("device/test/reporter", "{bad json}", "解析失败", "JsonProcessingException");

        assertEquals(1, deadLetterService.getDeadLetterCount());
        List<DeadLetterMessage> list = deadLetterService.getRecentDeadLetters();
        assertEquals(1, list.size());
        assertEquals("device/test/reporter", list.get(0).getTopic());
        assertEquals("{bad json}", list.get(0).getRawPayload());
        assertEquals("解析失败", list.get(0).getReason());
    }

    @Test
    @DisplayName("死信缓冲区溢出覆盖最旧")
    void testOverflow() {
        for (int i = 0; i < 150; i++) {
            deadLetterService.capture("topic", "payload-" + i, "reason-" + i, "Exception");
        }
        assertEquals(100, deadLetterService.getDeadLetterCount());
        List<DeadLetterMessage> list = deadLetterService.getRecentDeadLetters();
        assertEquals("reason-50", list.get(0).getReason());
        assertEquals("reason-149", list.get(list.size() - 1).getReason());
    }
}
