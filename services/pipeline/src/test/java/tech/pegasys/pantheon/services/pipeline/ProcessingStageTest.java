/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProcessingStageTest {

  private final Pipe<String> inputPipe = new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER);
  private final Pipe<String> outputPipe = new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER);
  @Mock private Processor<String, String> singleStep;
  private ProcessingStage<String, String> stage;

  @Before
  public void setUp() {
    stage = new ProcessingStage<>("name", inputPipe, outputPipe, singleStep);
    doAnswer(
            invocation -> {
              outputPipe.put(inputPipe.get().toLowerCase(Locale.UK));
              return 1;
            })
        .when(singleStep)
        .processNextInput(inputPipe, outputPipe);
  }

  @Test
  public void shouldCallSingleStepStageForEachInput() {
    inputPipe.put("A");
    inputPipe.put("B");
    inputPipe.put("C");
    inputPipe.close();

    stage.run();

    assertThat(outputPipe.poll()).isEqualTo("a");
    assertThat(outputPipe.poll()).isEqualTo("b");
    assertThat(outputPipe.poll()).isEqualTo("c");
    assertThat(outputPipe.poll()).isNull();

    verify(singleStep, times(3)).processNextInput(inputPipe, outputPipe);
  }

  @Test
  public void shouldFinalizeSingleStepStageAndCloseOutputPipeWhenInputCloses() {
    inputPipe.close();

    stage.run();

    verify(singleStep).finalize(outputPipe);
    verifyNoMoreInteractions(singleStep);
    assertThat(outputPipe.isOpen()).isFalse();
  }

  @Test
  public void shouldAbortProcessorIfReadPipeIsAborted() {
    inputPipe.abort();
    stage.run();

    verify(singleStep).abort();
    verify(singleStep).finalize(outputPipe);
    assertThat(outputPipe.isOpen()).isFalse();
  }
}
