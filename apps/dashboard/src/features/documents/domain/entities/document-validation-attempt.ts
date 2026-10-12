export type DocumentValidationAttempt = Readonly<{
  jobId: string;
  number: number;
  actorId: string;
  createdAt: string;
  reason: string;
}>;
