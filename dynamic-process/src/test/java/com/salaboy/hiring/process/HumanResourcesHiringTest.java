/*
 * Copyright 2013 JBoss by Red Hat.
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
package com.salaboy.hiring.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.runtime.manager.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;

import bitronix.tm.resource.jdbc.PoolingDataSource;

import javax.enterprise.inject.spi.BeanManager;
import org.jbpm.runtime.manager.impl.task.SynchronizedTaskService;
import org.jbpm.services.task.impl.TaskServiceEntryPointImpl;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.kie.api.runtime.manager.Context;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Content;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;

/**
 *
 * @author salaboy
 */
@RunWith(Arquillian.class)
public class HumanResourcesHiringTest {

    @Deployment()
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "hiring-example.jar")
                .addPackage("org.jboss.seam.persistence") //seam-persistence
                .addPackage("org.jboss.seam.transaction") //seam-persistence
                .addPackage("org.jbpm.services.task")
                .addPackage("org.jbpm.services.task.wih") // work items org.jbpm.services.task.wih
                .addPackage("org.jbpm.services.task.annotations")
                .addPackage("org.jbpm.services.task.api")
                .addPackage("org.jbpm.services.task.impl")
                .addPackage("org.jbpm.services.task.events")
                .addPackage("org.jbpm.services.task.exception")
                .addPackage("org.jbpm.services.task.identity")
                .addPackage("org.jbpm.services.task.factories")
                .addPackage("org.jbpm.services.task.internals")
                .addPackage("org.jbpm.services.task.internals.lifecycle")
                .addPackage("org.jbpm.services.task.lifecycle.listeners")
                .addPackage("org.jbpm.services.task.query")
                .addPackage("org.jbpm.services.task.util")
                .addPackage("org.jbpm.services.task.commands") // This should not be required here
                .addPackage("org.jbpm.services.task.deadlines") // deadlines
                .addPackage("org.jbpm.services.task.deadlines.notifications.impl")
                .addPackage("org.jbpm.services.task.subtask")
                .addPackage("org.kie.internal.runtime")
                .addPackage("org.kie.internal.runtime.manager")
                .addPackage("org.kie.internal.runtime.manager.cdi.qualifier")
                .addPackage("org.jbpm.runtime.manager")
                .addPackage("org.jbpm.runtime.manager.impl")
                .addPackage("org.jbpm.runtime.manager.impl.cdi")
                .addPackage("org.jbpm.runtime.manager.impl.cdi.qualifier")
                .addPackage("org.jbpm.runtime.manager.impl.context")
                .addPackage("org.jbpm.runtime.manager.impl.factory")
                .addPackage("org.jbpm.runtime.manager.impl.jpa")
                .addPackage("org.jbpm.runtime.manager.impl.manager")
                .addPackage("org.jbpm.runtime.manager.mapper")
                .addPackage("org.jbpm.runtime.manager.impl.task")
                .addPackage("org.jbpm.runtime.manager.impl.tx")
                .addPackage("org.jbpm.shared.services.api")
                .addPackage("org.jbpm.shared.services.impl")
                .addPackage("org.jbpm.kie.services.api")
                .addPackage("org.jbpm.kie.services.impl")
                .addPackage("org.jbpm.kie.services.api.bpmn2")
                .addPackage("org.jbpm.kie.services.impl.bpmn2")
                .addPackage("org.jbpm.kie.services.impl.event.listeners")
                .addPackage("org.jbpm.kie.services.impl.audit")
                .addPackage("org.jbpm.kie.services.impl.util")
                .addPackage("org.jbpm.kie.services.impl.vfs")
                .addPackage("org.jbpm.kie.services.impl.example")
                .addPackage("org.kie.commons.java.nio.fs.jgit")
                .addPackage("com.salaboy.hiring.process")
                .addAsResource("jndi.properties", "jndi.properties")
                .addAsManifestResource("META-INF/persistence.xml", ArchivePaths.create("persistence.xml"))
                //                .addAsManifestResource("META-INF/Taskorm.xml", ArchivePaths.create("Taskorm.xml"))
                .addAsManifestResource("META-INF/beans.xml", ArchivePaths.create("beans.xml"));

    }
    private static PoolingDataSource pds;

    @BeforeClass
    public static void setup() {
        TestUtil.cleanupSingletonSessionId();
        pds = TestUtil.setupPoolingDataSource();


    }

    @AfterClass
    public static void teardown() {
        pds.close();
    }

    @After
    public void tearDownTest() {
    }
    @Inject
    private EntityManagerFactory emf;
    @Inject
    private BeanManager beanManager;
    @Inject
    private RuntimeManagerFactory managerFactory;

    @Test
    public void simpleExecutionTest() {
        assertNotNull(managerFactory);
        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.getDefault()
                .entityManagerFactory(emf)
                .registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, null));

        builder.addAsset(ResourceFactory.newClassPathResource("repo/hiring.bpmn2"), ResourceType.BPMN2);


        RuntimeManager manager = managerFactory.newSingletonRuntimeManager(builder.get());
        testHiringProcess(manager, EmptyContext.get());

        manager.close();

    }

    private void testHiringProcess(RuntimeManager manager, Context context) {

        RuntimeEngine runtime = manager.getRuntimeEngine(context);
        KieSession ksession = runtime.getKieSession();
        TaskService taskService = runtime.getTaskService();


        assertNotNull(runtime);
        assertNotNull(ksession);

        ProcessInstance processInstance = ksession.startProcess("hiring");

        List<TaskSummary> tasks = ((SynchronizedTaskService)taskService).getTasksAssignedByGroup("HR", "en-UK");

        TaskSummary HRInterview = tasks.get(0);

        taskService.claim(HRInterview.getId(), "katy");

        taskService.start(HRInterview.getId(), "katy");

        Map<String, Object> hrOutput = new HashMap<String, Object>();
        hrOutput.put("out_name", "salaboy");
        hrOutput.put("out_age", 29);
        hrOutput.put("out_mail", "salaboy@gmail.com");
        hrOutput.put("out_score", 8);

        taskService.complete(HRInterview.getId(), "katy", hrOutput);


        assertNotNull(processInstance);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        tasks = ((SynchronizedTaskService)taskService).getTasksAssignedByGroup("IT", "en-UK");
       
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary techInterview = tasks.get(0);
        Task techInterviewTask = taskService.getTaskById(techInterview.getId());
        Content contentById = taskService.getContentById(techInterviewTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);

        Map<String, Object> taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(5, taskContent.size());

        assertEquals("salaboy@gmail.com", taskContent.get("in_mail"));
        assertEquals(29, taskContent.get("in_age"));
        assertEquals("salaboy", taskContent.get("in_name"));

        taskService.claim(techInterview.getId(), "salaboy");

        taskService.start(techInterview.getId(), "salaboy");


        Map<String, Object> techOutput = new HashMap<String, Object>();
        techOutput.put("out_skills", "java, jbpm, drools");
        techOutput.put("out_twitter", "@salaboy");
        techOutput.put("out_score", 8);

        taskService.complete(techInterview.getId(), "salaboy", techOutput);


        tasks = taskService.getTasksAssignedAsPotentialOwner("Accounting", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary createProposal = tasks.get(0);

        Task createProposalTask = taskService.getTaskById(createProposal.getId());
        contentById = taskService.getContentById(createProposalTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);
        taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(4, taskContent.size());

        assertEquals(8, taskContent.get("in_tech_score"));
        assertEquals(8, taskContent.get("in_hr_score"));


        taskService.claim(createProposal.getId(), "john");

        taskService.start(createProposal.getId(), "john");

        Map<String, Object> proposalOutput = new HashMap<String, Object>();
        proposalOutput.put("out_offering", 10000);


        taskService.complete(createProposal.getId(), "john", proposalOutput);

        
        tasks = ((SynchronizedTaskService)taskService).getTasksAssignedByGroup("HR", "en-UK");
        
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        TaskSummary signContract = tasks.get(0);

        Task signContractTask = taskService.getTaskById(signContract.getId());
        contentById = taskService.getContentById(signContractTask.getTaskData().getDocumentContentId());
        assertNotNull(contentById);
        taskContent = (Map<String, Object>) ContentMarshallerHelper.unmarshall(contentById.getContent(), null);

        assertEquals(4, taskContent.size());

        assertEquals(10000, taskContent.get("in_offering"));
        assertEquals("salaboy", taskContent.get("in_name"));

        taskService.claim(signContract.getId(), "katy");

        taskService.start(signContract.getId(), "katy");

        Map<String, Object> signOutput = new HashMap<String, Object>();
        signOutput.put("out_signed", true);
        taskService.complete(signContract.getId(), "katy", signOutput);

        
        int removeAllTasks = ((SynchronizedTaskService)taskService).removeAllTasks();
        System.out.println(">>> Removed Tasks > " + removeAllTasks);
    }
}
