package com.dwinovo.numen.agent.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransientImageLogTest {

    @TempDir
    Path temp;

    @Test
    void conversationLogPersistsTextButNeverImageBytes() throws Exception {
        Path file = temp.resolve("conversation.jsonl");
        ConvoLog log = ConvoLog.atFile(file);
        InputImage image = new InputImage("image/png",
                "secret-image-payload".getBytes(StandardCharsets.UTF_8));
        log.append(new ConvoState.Msg.User("看看这个", List.of(image)));

        String disk = Files.readString(file);
        assertFalse(disk.contains("secret-image-payload"));
        assertFalse(disk.contains(image.base64()));
        ConvoState.Msg.User loaded = (ConvoState.Msg.User) log.load(20).get(0);
        assertEquals("看看这个", loaded.content());
        assertFalse(loaded.hasImages());
    }
}
