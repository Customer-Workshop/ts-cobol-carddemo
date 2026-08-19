package com.carddemo.api;

import com.carddemo.batch.BatchJobLauncherService;
import org.springframework.batch.core.JobExecution;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/jobs")
public class BatchAdminController {
    private final BatchJobLauncherService launcher;

    public BatchAdminController(BatchJobLauncherService launcher) {
        this.launcher = launcher;
    }

    @PostMapping("/{jobName}")
    public JobLaunchResponse launch(@PathVariable String jobName,
                                    @RequestParam Map<String, String> parameters) {
        JobExecution execution = launcher.launch(jobName, parameters);
        return new JobLaunchResponse(jobName, execution.getId(), execution.getStatus().name());
    }
}
