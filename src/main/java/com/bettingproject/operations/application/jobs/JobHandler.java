package com.bettingproject.operations.application.jobs;

import com.bettingproject.operations.domain.JobModel.*;

public interface JobHandler {
    Type type();
    Outcome execute(Claim claim);
}
