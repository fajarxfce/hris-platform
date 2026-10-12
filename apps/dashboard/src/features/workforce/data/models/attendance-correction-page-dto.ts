import { z } from "zod";
import { attendanceCorrectionDto } from "./attendance-dto";

export const attendanceCorrectionPageDto = z.object({
  items: z.array(attendanceCorrectionDto).max(50),
  nextCursor: z.string().min(1).max(16).nullable(),
});
export type AttendanceCorrectionPageDto = z.infer<typeof attendanceCorrectionPageDto>;
