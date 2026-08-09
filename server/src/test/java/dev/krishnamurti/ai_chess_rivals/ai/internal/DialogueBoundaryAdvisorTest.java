package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

class DialogueBoundaryAdvisorTest {

  @Test
  void augmentsSystemBoundaryWithoutChangingUserPrompt() {
    DialogueBoundaryAdvisor advisor = new DialogueBoundaryAdvisor();
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    ChatClientResponse response = mock(ChatClientResponse.class);
    when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(response);
    ChatClientRequest request =
        ChatClientRequest.builder().prompt(new Prompt("current event prompt")).build();

    advisor.adviseCall(request, chain);

    ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
    verify(chain).nextCall(captor.capture());
    assertThat(captor.getValue().prompt().getSystemMessage().getText())
        .contains("fictional PG-13 chess-rivalry dialogue")
        .contains("Never calculate, choose, validate, or recommend chess moves");
    assertThat(captor.getValue().prompt().getUserMessage().getText())
        .contains("current event prompt");
  }
}
