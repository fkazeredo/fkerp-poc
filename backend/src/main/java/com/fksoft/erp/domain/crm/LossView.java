package com.fksoft.erp.domain.crm;

import java.time.Instant;

/** Read view of a Lead's loss outcome. */
public record LossView(String lossReasonLabel, Instant lostAt, String lostByName, String note) {}
