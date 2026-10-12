package com.algrex.campus.alerts.domain;

/** Workflow vocabulary only; transition rules remain member D's implementation task. */
public enum AlertStatus {
  PENDING,
  ACCEPTED,
  INTERVENING,
  AWAITING_REASSESSMENT,
  CLOSED
}
