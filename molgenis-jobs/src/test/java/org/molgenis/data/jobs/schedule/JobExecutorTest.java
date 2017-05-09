package org.molgenis.data.jobs.schedule;

import org.mockito.Mock;
import org.molgenis.data.AbstractMolgenisSpringTest;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityManager;
import org.molgenis.data.config.UserTestConfig;
import org.molgenis.data.jobs.Job;
import org.molgenis.data.jobs.JobExecutionTemplate;
import org.molgenis.data.jobs.JobFactory;
import org.molgenis.data.jobs.Progress;
import org.molgenis.data.jobs.config.JobTestConfig;
import org.molgenis.data.jobs.model.JobExecution;
import org.molgenis.data.jobs.model.JobType;
import org.molgenis.data.jobs.model.ScheduledJob;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.util.GsonConfig;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.molgenis.data.jobs.model.ScheduledJobMetadata.SCHEDULED_JOB;

@ContextConfiguration(classes = { JobExecutorTest.Config.class, JobExecutor.class, JobTestConfig.class })
public class JobExecutorTest extends AbstractMolgenisSpringTest
{
	@Autowired
	private Config config;

	@Autowired
	private DataService dataService;

	@Autowired
	JobExecutor jobExecutor;

	@Autowired
	JobFactory jobFactory;

	@Autowired
	JobType jobType;

	@Autowired
	UserDetailsService userDetailsService;

	@Autowired
	JobExecutionTemplate jobExecutionTemplate;

	@Mock
	private ScheduledJob scheduledJob;

	@Mock
	private Job<Void> job;

	@Mock
	private JobExecutionContext jobExecutionContext;

	@Autowired
	private EntityManager entityManager;

	@Mock
	private EntityType jobExecutionType;

	@Mock
	TestJobExecution jobExecution;

	@Mock
	private UserDetails userDetails;

	@Mock
	private GrantedAuthority grantedAuthority1;

	@Mock
	private GrantedAuthority grantedAuthority2;

	@BeforeClass
	public void beforeClass()
	{
		initMocks(this);

	}

	@BeforeMethod
	public void beforeMethod()
	{
		config.resetMocks();
		reset(jobExecutionContext);
		when(jobFactory.getJobType()).thenReturn(jobType);
		when(jobType.getJobExecutionType()).thenReturn(jobExecutionType);
		when(jobType.getName()).thenReturn("jobName");
	}

	@Test
	public void executeScheduledJob() throws Exception
	{
		when(dataService.findOneById(SCHEDULED_JOB, "aaaacw67ejuwq7wron3yjriaae", ScheduledJob.class))
				.thenReturn(scheduledJob);
		when(entityManager.create(jobExecutionType, EntityManager.CreationMode.POPULATE)).thenReturn(jobExecution);

		when(jobFactory.createJob(jobExecution)).thenReturn(job);
		when(scheduledJob.getParameters()).thenReturn("{param1:'param1Value', param2:2}");
		when(scheduledJob.getFailureEmail()).thenReturn("x@y.z");
		when(scheduledJob.getSuccessEmail()).thenReturn("a@b.c");
		when(scheduledJob.getUser()).thenReturn("fjant");
		when(scheduledJob.getType()).thenReturn(jobType);

		when(userDetailsService.loadUserByUsername("fjant")).thenReturn(userDetails);

		Collection<? extends GrantedAuthority> authorities = Arrays.asList(grantedAuthority1, grantedAuthority2);
		when(userDetails.getAuthorities()).thenAnswer(i -> authorities);

		when(jobExecution.getEntityType()).thenReturn(jobExecutionType);
		when(jobExecutionType.getId()).thenReturn("sys_FileIngestJobExecution");
		when(jobExecution.getUser()).thenReturn("fjant");

		jobExecutor.executeScheduledJob("aaaacw67ejuwq7wron3yjriaae");

		verify(jobExecution).setFailureEmail("x@y.z");
		verify(jobExecution).setSuccessEmail("a@b.c");
		verify(jobExecution).setParam1("param1Value");
		verify(jobExecution).setParam2(2);
		verify(jobExecution).setDefaultValues();

		verify(dataService).add("sys_FileIngestJobExecution", jobExecution);

		verify(jobExecutionTemplate).call(eq(job), any(Progress.class), any(Authentication.class));
	}

	public static class TestJobExecution extends JobExecution
	{
		private String param1;
		private int param2;

		public TestJobExecution(Entity entity)
		{
			super(entity);
		}

		public void setParam1(String param1)
		{
			this.param1 = param1;
		}

		public String getParam1()
		{
			return param1;
		}

		public void setParam2(int param2)
		{
			this.param2 = param2;
		}

		public int getParam2()
		{
			return param2;
		}
	}

	@Configuration
	@Import({ UserTestConfig.class, JobTestConfig.class, GsonConfig.class })
	public static class Config
	{
		public Config()
		{
			initMocks(this);
		}

		@Mock
		JobFactory jobFactory;

		@Mock
		JobType jobType;

		public void resetMocks()
		{
			reset(jobFactory, jobType);
		}

		@Bean
		public JobFactory jobFactory()
		{
			return jobFactory;
		}

		@Bean
		JobType jobType()
		{
			return jobType;
		}

		@Bean
		public MailSender mailSender()
		{
			return mock(MailSender.class);
		}

		@Bean
		public UserDetailsService userDetailsService()
		{
			return mock(UserDetailsService.class);
		}

		@Bean
		public EntityManager entityManager()
		{
			return mock(EntityManager.class);
		}
	}
}
