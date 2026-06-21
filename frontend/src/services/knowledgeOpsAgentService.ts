import { api } from "./api";

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface KnowledgeOpsRunRequest {
  kbId: string;
  task: string;
  topK?: number;
  enableLlmEvaluation?: boolean;
  scenario?: string;
  workflow?: string[];
  benchmarkQuestions?: string[];
}

export interface KnowledgeOpsReport {
  coverageLevel?: string;
  coverageScore?: number;
  scenario?: string;
  planReason?: string;
  summary?: string;
  findings?: string[];
  recommendations?: string[];
  metrics?: Record<string, unknown>;
  markdown?: string;
}

export interface KnowledgeOpsStep {
  id: string;
  runId: string;
  stepOrder: number;
  stepType: string;
  toolName: string;
  status: string;
  inputJson?: string | null;
  outputJson?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface KnowledgeOpsRun {
  id: string;
  kbId: string;
  task: string;
  status: string;
  summary?: string | null;
  errorMessage?: string | null;
  report?: KnowledgeOpsReport | null;
  steps?: KnowledgeOpsStep[] | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createTime?: string | null;
}

export const runKnowledgeOpsAgent = async (payload: KnowledgeOpsRunRequest): Promise<KnowledgeOpsRun> => {
  return api.post<KnowledgeOpsRun, KnowledgeOpsRun>("/agent/knowledge-ops/runs", payload);
};

export const getKnowledgeOpsRuns = async (
  current = 1,
  size = 10,
  kbId?: string
): Promise<PageResult<KnowledgeOpsRun>> => {
  return api.get<PageResult<KnowledgeOpsRun>, PageResult<KnowledgeOpsRun>>("/agent/knowledge-ops/runs", {
    params: {
      current,
      size,
      kbId: kbId || undefined
    }
  });
};

export const getKnowledgeOpsRun = async (runId: string): Promise<KnowledgeOpsRun> => {
  return api.get<KnowledgeOpsRun, KnowledgeOpsRun>(`/agent/knowledge-ops/runs/${runId}`);
};
