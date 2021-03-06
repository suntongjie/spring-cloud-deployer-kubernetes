/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.kubernetes;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.failed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.task.LaunchState.complete;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationTests;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Integration tests for {@link KubernetesTaskLauncher}.
 *
 * @author Thomas Risberg
 */
@SpringApplicationConfiguration(classes = {KubernetesAutoConfiguration.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class KubernetesTaskLauncherIntegrationTests {

	protected static final Logger logger = LoggerFactory.getLogger(KubernetesTaskLauncherIntegrationTests.class);

	@ClassRule
	public static KubernetesTestSupport kubernetesAvailable = new KubernetesTestSupport();

	@Autowired
	private TaskLauncher taskLauncher;

	@Autowired
	KubernetesClient kubernetesClient;

	@Autowired
	ContainerFactory containerFactory;

//	@Override
	protected TaskLauncher taskLauncher() {
		return taskLauncher;
	}


	@Test
	public void testSimpleLaunch() {
		logger.info("Testing {}...", "SimpleLaunch");
		AppDefinition definition = new AppDefinition(this.randomName(), (Map)null);
		Resource resource = integrationTestTask();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
		logger.info("Launching {}...", request.getDefinition().getName());
		String deploymentId = taskLauncher.launch(request);

		Timeout timeout = launchTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", is(complete))), timeout.maxAttempts, timeout.pause));

		((KubernetesTaskLauncher)taskLauncher).delete(deploymentId);
	}

	protected String randomName() {
		// Kubernetest app names must start with a letter and can only be 24 characters long
		return "job-" + UUID.randomUUID().toString().substring(0, 18);
	}

	//	@Override
	protected Resource integrationTestTask() {
		return new DockerResource("trisberg/deployer-test-task");
	}

	protected Timeout launchTimeout() {
		return new Timeout(20, 5000);
	}

	protected Matcher<String> hasStatusThat(final Matcher<TaskStatus> statusMatcher) {
		return new BaseMatcher() {
			private TaskStatus status;

			public boolean matches(Object item) {
				this.status = KubernetesTaskLauncherIntegrationTests.this.taskLauncher.status((String)item);
				return statusMatcher.matches(this.status);
			}

			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("status of ").appendValue(item).appendText(" ");
				statusMatcher.describeMismatch(this.status, mismatchDescription);
			}

			public void describeTo(Description description) {
				statusMatcher.describeTo(description);
			}
		};
	}

	protected static class Timeout {
		public final int maxAttempts;
		public final int pause;

		public Timeout(int maxAttempts, int pause) {
			this.maxAttempts = maxAttempts;
			this.pause = pause;
		}
	}
}
