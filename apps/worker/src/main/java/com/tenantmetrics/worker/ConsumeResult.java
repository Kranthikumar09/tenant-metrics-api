package com.tenantmetrics.worker;

record ConsumeResult(boolean accepted, String tenantId, String eventId, String rejection) {
}
