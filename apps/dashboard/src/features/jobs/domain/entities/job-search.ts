export type JobSearch = Readonly<{ beforeAt: string | null; beforeId: string | null }>;
export const firstJobPage: JobSearch = Object.freeze({ beforeAt: null, beforeId: null });
