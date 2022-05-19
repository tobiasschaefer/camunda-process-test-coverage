package org.camunda.community.process_test_coverage.examples.spring_starter.platform8;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import org.camunda.community.process_test_coverage.core.engine.ExcludeFromProcessCoverage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static io.camunda.zeebe.protocol.Protocol.USER_TASK_JOB_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ZeebeSpringTest
@Import(CoverageTestConfiguration.class)
public class OrderProcessTest {

    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private ZeebeTestEngine zeebeTestEngine;

    @Test
    public void shouldExecuteHappyPath() throws InterruptedException, TimeoutException {
        final ProcessInstanceEvent instance = this.startProcess();

        waitForUserTaskAndComplete("Task_ProcessOrder", Collections.singletonMap("orderOk", true));

        assertThat(instance).isWaitingAtElements("Task_DeliverOrder");

        waitForUserTaskAndComplete("Task_DeliverOrder", Collections.emptyMap());

        assertThat(instance)
                .hasPassedElement("Event_OrderProcessed")
                .isCompleted();
    }

    @Test
    public void shouldCancelOrder() throws Exception {
        final ProcessInstanceEvent instance = this.startProcess();

        waitForUserTaskAndComplete("Task_ProcessOrder", Collections.singletonMap("orderOk", false));

        assertThat(instance)
                .hasPassedElement("Event_OrderCancelled")
                .isCompleted();
    }

    @Test
    @ExcludeFromProcessCoverage
    public void testSomethingElse() {
        Assertions.assertTrue(true);
    }

    private void waitForUserTaskAndComplete(String userTaskId, Map<String, Object> variables) throws InterruptedException, TimeoutException {
        // Let the workflow engine do whatever it needs to do
        zeebeTestEngine.waitForIdleState(Duration.ofSeconds(10));

        // Now get all user tasks
        List<ActivatedJob> jobs = zeebe.newActivateJobsCommand().jobType(USER_TASK_JOB_TYPE).maxJobsToActivate(1).workerName("waitForUserTaskAndComplete").send().join().getJobs();

        // Should be only one
        assertTrue(jobs.size()>0, "Job for user task '" + userTaskId + "' does not exist");
        ActivatedJob userTaskJob = jobs.get(0);
        // Make sure it is the right one
        if (userTaskId!=null) {
            assertEquals(userTaskId, userTaskJob.getElementId());
        }

        // And complete it passing the variables
        if (variables!=null && variables.size()>0) {
            zeebe.newCompleteCommand(userTaskJob.getKey()).variables(variables).send().join();
        } else {
            zeebe.newCompleteCommand(userTaskJob.getKey()).send().join();
        }
        // Let the workflow engine do whatever it needs to do
        zeebeTestEngine.waitForIdleState(Duration.ofSeconds(10));

    }

    private ProcessInstanceEvent startProcess() {
        return zeebe.newCreateInstanceCommand() //
                .bpmnProcessId("order-process")
                .latestVersion() //
                .send().join();
    }


}
