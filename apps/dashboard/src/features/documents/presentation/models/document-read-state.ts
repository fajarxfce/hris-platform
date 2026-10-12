import type { Failure } from "../../../../core/domain/result";

export type DocumentReadState<T> = Readonly<{
  loading: boolean;
  data: T | null;
  failure: Failure | null;
  refresh: () => void;
}>;
