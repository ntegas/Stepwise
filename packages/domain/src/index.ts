// Shared types mirroring /supabase/migrations/0001_init.sql.
// Derived numbers (rollups, execution rate, pace, forecast) are computed by
// Postgres views, not here — this package holds the shapes both the web app
// and a future Android client agree on, plus small pure helpers that don't
// depend on a database round trip (e.g. optimistic UI updates).

export type GoalType =
  | "cumulative"
  | "quantity"
  | "distance"
  | "financial"
  | "target"
  | "frequency"
  | "percentage";

export type SessionStatus =
  | "done"
  | "partial"
  | "missed"
  | "rescheduled"
  | "in_progress"
  | "cancelled";

export type SessionSource = "task" | "activity" | "habit";

export interface Goal {
  id: string;
  user_id: string;
  title: string;
  type: GoalType;
  target_value: number | null;
  current_value: number;
  unit: string | null;
  deadline: string | null;
  priority: number | null;
  project_id: string | null;
  category: string | null;
  description: string | null;
}

export interface Activity {
  id: string;
  user_id: string;
  title: string;
  icon: string | null;
  default_goal_id: string | null;
  recurrence_rule: string | null;
}

export interface Task {
  id: string;
  user_id: string;
  goal_id: string | null;
  project_id: string | null;
  title: string;
  date: string | null;
  time: string | null;
  deadline: string | null;
  duration: number | null;
  priority: number | null;
  status: SessionStatus;
}

export interface Habit {
  id: string;
  user_id: string;
  activity_id: string | null;
  goal_id: string | null;
  title: string;
  target_per_period: number | null;
  unit: string | null;
  recurrence: string | null;
}

export interface Session {
  id: string;
  user_id: string;
  source_type: SessionSource;
  source_id: string;
  date: string;
  planned_amount: number | null;
  actual_amount: number | null;
  status: SessionStatus;
  notes: string | null;
}

/**
 * One-tap complete default (concept §5, §43): if the caller doesn't override
 * actual, assume the planned amount was fully done.
 */
export function defaultActualAmount(plannedAmount: number | null): number | null {
  return plannedAmount;
}

export function executionPct(planned: number, actual: number): number | null {
  if (!planned) return null;
  return Math.round((actual / planned) * 1000) / 10;
}
