package com.dwinovo.numen.agent.provider;

import com.dwinovo.numen.agent.llm.ConvoState;
import com.dwinovo.numen.agent.llm.InputImage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultimodalMessageTest {

    private static final InputImage IMAGE = new InputImage(
            "image/png", "fake-png".getBytes(StandardCharsets.UTF_8));

    @Test
    void openAiUsesDataUrlImageBlocks() {
        JsonObject message = new OpenAIProvider().buildUserMessage("看图", List.of(IMAGE));
        JsonArray blocks = message.getAsJsonArray("content");
        assertEquals("text", blocks.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("看图", blocks.get(0).getAsJsonObject().get("text").getAsString());
        JsonObject image = blocks.get(1).getAsJsonObject();
        assertEquals("image_url", image.get("type").getAsString());
        assertTrue(image.getAsJsonObject("image_url").get("url").getAsString()
                .startsWith("data:image/png;base64,"));
    }

    @Test
    void anthropicUsesBase64SourceBlocks() {
        JsonObject message = new AnthropicProvider().buildUserMessage("看图", List.of(IMAGE));
        JsonArray blocks = message.getAsJsonArray("content");
        JsonObject image = blocks.get(0).getAsJsonObject();
        assertEquals("image", image.get("type").getAsString());
        assertEquals("base64", image.getAsJsonObject("source").get("type").getAsString());
        assertEquals("image/png", image.getAsJsonObject("source").get("media_type").getAsString());
        assertEquals("text", blocks.get(1).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void textOnlyMessagesKeepTheirLegacyStringShape() {
        JsonObject message = new OpenAIProvider().buildUserMessage("你好", List.of());
        assertTrue(message.get("content").isJsonPrimitive());
        assertEquals("你好", message.get("content").getAsString());
    }

    @Test
    void settledConversationReleasesImagesButKeepsText() {
        ConvoState state = new ConvoState();
        state.addUser("看一下", List.of(IMAGE));
        assertTrue(((ConvoState.Msg.User) state.snapshot().get(0)).hasImages());
        state.stripImages();
        ConvoState.Msg.User user = (ConvoState.Msg.User) state.snapshot().get(0);
        assertEquals("看一下", user.content());
        assertFalse(user.hasImages());
    }
}
