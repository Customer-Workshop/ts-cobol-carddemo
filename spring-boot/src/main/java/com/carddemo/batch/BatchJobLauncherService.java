package com.carddemo.batch;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.CobolApiException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class BatchJobLauncherService {
    private final JobLauncher launcher;
    private final Map<String, Job> jobs;

    public BatchJobLauncherService(JobLauncher launcher, Map<String, Job> jobs) {
        this.launcher = launcher;
        this.jobs = jobs;
    }

    public JobExecution launch(String jobName, Map<String, String> values) {
        Job job = jobs.get(jobName);
        if (job == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.unknownBatchJob(jobName));
        }
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("run.id", System.nanoTime());
        values.forEach((key, value) -> builder.addString(key, value));
        try {
            return launcher.run(job, builder.toJobParameters());
        } catch (JobParametersInvalidException exception) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to launch batch job " + jobName, exception);
        }
    }
}
