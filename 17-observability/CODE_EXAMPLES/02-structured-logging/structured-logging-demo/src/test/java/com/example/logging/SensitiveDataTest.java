package com.example.logging;

import com.example.logging.CardLogging.Card;
import com.example.logging.CardLogging.RawCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataTest {

    @Autowired CardLogging cards;

    @Test
    void loggingAWholeObject_logsEveryFieldInIt(CapturedOutput output) {
        cards.chargeBroken(new RawCard("Ann", "4111111111111111"));

        assertThat(output.getOut()).contains("4111111111111111");
    }

    @Test
    void aMaskingToString_keepsTheNumberOutOfTheLogs(CapturedOutput output) {
        cards.charge(new Card("Ann", "4111111111111111"));

        assertThat(output.getOut()).doesNotContain("4111111111111111").contains("number=****1111");
    }
}
