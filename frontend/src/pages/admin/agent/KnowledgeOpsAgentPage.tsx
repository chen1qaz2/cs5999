import { useEffect, useMemo, useState } from "react";
import { Activity, Bot, CheckCircle2, FileText, Loader2, Play, Search, ShieldAlert } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import {
  getKnowledgeOpsRuns,
  runKnowledgeOpsAgent,
  type KnowledgeOpsRun,
  type KnowledgeOpsStep
} from "@/services/knowledgeOpsAgentService";

const DEFAULT_TASK = "Evaluate whether this knowledge base can answer employee reimbursement process questions.";

function statusVariant(status?: string) {
  if (status === "SUCCESS") return "default";
  if (status === "FAILED") return "destructive";
  return "secondary";
}

function parseToolSummary(step: KnowledgeOpsStep) {
  if (!step.outputJson) return step.errorMessage || "No output yet";
  try {
    const parsed = JSON.parse(step.outputJson);
    return parsed.summary || step.status;
  } catch {
    return step.status;
  }
}

export function KnowledgeOpsAgentPage() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [kbId, setKbId] = useState("");
  const [task, setTask] = useState(DEFAULT_TASK);
  const [running, setRunning] = useState(false);
  const [activeRun, setActiveRun] = useState<KnowledgeOpsRun | null>(null);
  const [history, setHistory] = useState<KnowledgeOpsRun[]>([]);

  useEffect(() => {
    getKnowledgeBases(1, 100)
      .then((items) => {
        setKnowledgeBases(items);
        if (!kbId && items.length > 0) {
          setKbId(items[0].id);
        }
      })
      .catch((error) => toast.error((error as Error).message || "Failed to load knowledge bases"));
  }, []);

  const selectedKb = useMemo(
    () => knowledgeBases.find((item) => item.id === kbId),
    [knowledgeBases, kbId]
  );

  const refreshHistory = async () => {
    const page = await getKnowledgeOpsRuns(1, 8, kbId || undefined);
    setHistory(page.records || []);
  };

  useEffect(() => {
    if (kbId) {
      refreshHistory().catch(() => setHistory([]));
    }
  }, [kbId]);

  const handleRun = async () => {
    if (!kbId) {
      toast.error("Please select a knowledge base");
      return;
    }
    if (!task.trim()) {
      toast.error("Please enter an agent task");
      return;
    }
    try {
      setRunning(true);
      const run = await runKnowledgeOpsAgent({
        kbId,
        task: task.trim(),
        topK: 8,
        enableLlmEvaluation: false
      });
      setActiveRun(run);
      await refreshHistory();
      toast.success("KnowledgeOps Agent run completed");
    } catch (error) {
      toast.error((error as Error).message || "Agent run failed");
    } finally {
      setRunning(false);
    }
  };

  const steps = activeRun?.steps || [];
  const report = activeRun?.report;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">KnowledgeOps Agent</h2>
          <p className="mt-1 text-sm text-slate-500">
            Autonomous knowledge-base analysis powered by retrieval tools, quality inspection, and coverage scoring.
          </p>
        </div>
        <Badge variant="outline" className="w-fit">
          Agentic AI Native Module
        </Badge>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Bot className="h-5 w-5 text-indigo-600" />
                Run Agent
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 md:grid-cols-[280px_minmax(0,1fr)]">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700">Knowledge base</label>
                  <Select value={kbId} onValueChange={setKbId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select knowledge base" />
                    </SelectTrigger>
                    <SelectContent>
                      {knowledgeBases.map((kb) => (
                        <SelectItem key={kb.id} value={kb.id}>
                          {kb.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {selectedKb ? (
                    <p className="text-xs text-slate-500">Collection: {selectedKb.collectionName || "not configured"}</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700">Agent task</label>
                  <Textarea
                    value={task}
                    onChange={(event) => setTask(event.target.value)}
                    className="min-h-[96px]"
                    placeholder="Describe what the agent should evaluate"
                  />
                </div>
              </div>
              <div className="flex justify-end">
                <Button onClick={handleRun} disabled={running || !kbId} className="gap-2">
                  {running ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                  {running ? "Running" : "Run KnowledgeOps Agent"}
                </Button>
              </div>
            </CardContent>
          </Card>

          <div className="grid gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Activity className="h-5 w-5 text-cyan-600" />
                  Agent Steps
                </CardTitle>
              </CardHeader>
              <CardContent>
                {steps.length === 0 ? (
                  <div className="rounded-md border border-dashed border-slate-200 p-6 text-sm text-slate-500">
                    Run the agent to see its tool execution trace.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {steps.map((step) => (
                      <div key={step.id} className="rounded-md border border-slate-200 bg-white p-3">
                        <div className="flex items-center justify-between gap-3">
                          <div className="flex min-w-0 items-center gap-2">
                            {step.status === "SUCCESS" ? (
                              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
                            ) : step.status === "FAILED" ? (
                              <ShieldAlert className="h-4 w-4 shrink-0 text-rose-600" />
                            ) : (
                              <Loader2 className="h-4 w-4 shrink-0 animate-spin text-slate-500" />
                            )}
                            <span className="truncate text-sm font-medium text-slate-800">{step.toolName}</span>
                          </div>
                          <Badge variant={statusVariant(step.status)}>{step.status}</Badge>
                        </div>
                        <p className="mt-2 text-xs leading-5 text-slate-500">{parseToolSummary(step)}</p>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <FileText className="h-5 w-5 text-emerald-600" />
                  Agent Report
                </CardTitle>
              </CardHeader>
              <CardContent>
                {!report ? (
                  <div className="rounded-md border border-dashed border-slate-200 p-8 text-sm text-slate-500">
                    No report yet.
                  </div>
                ) : (
                  <div className="space-y-5">
                    <div className="grid gap-3 sm:grid-cols-3">
                      <div className="rounded-md border border-slate-200 p-4">
                        <p className="text-xs uppercase text-slate-400">Coverage</p>
                        <p className="mt-2 text-xl font-semibold text-slate-900">{report.coverageLevel || "--"}</p>
                      </div>
                      <div className="rounded-md border border-slate-200 p-4">
                        <p className="text-xs uppercase text-slate-400">Score</p>
                        <p className="mt-2 text-xl font-semibold text-slate-900">{report.coverageScore ?? "--"}</p>
                      </div>
                      <div className="rounded-md border border-slate-200 p-4">
                        <p className="text-xs uppercase text-slate-400">Run</p>
                        <p className="mt-2 truncate text-sm font-medium text-slate-900">{activeRun?.id}</p>
                      </div>
                    </div>

                    <section>
                      <h3 className="text-sm font-semibold text-slate-800">Findings</h3>
                      <ul className="mt-2 space-y-2 text-sm text-slate-600">
                        {(report.findings || []).map((item) => (
                          <li key={item} className="rounded-md bg-slate-50 px-3 py-2">
                            {item}
                          </li>
                        ))}
                      </ul>
                    </section>

                    <section>
                      <h3 className="text-sm font-semibold text-slate-800">Recommendations</h3>
                      <ul className="mt-2 space-y-2 text-sm text-slate-600">
                        {(report.recommendations || []).map((item) => (
                          <li key={item} className="rounded-md bg-emerald-50 px-3 py-2 text-emerald-800">
                            {item}
                          </li>
                        ))}
                      </ul>
                    </section>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Search className="h-5 w-5 text-slate-600" />
              Recent Runs
            </CardTitle>
          </CardHeader>
          <CardContent>
            {history.length === 0 ? (
              <p className="text-sm text-slate-500">No recent runs.</p>
            ) : (
              <div className="space-y-3">
                {history.map((run) => (
                  <button
                    key={run.id}
                    type="button"
                    onClick={() => setActiveRun(run)}
                    className="w-full rounded-md border border-slate-200 bg-white p-3 text-left transition hover:bg-slate-50"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="truncate text-sm font-medium text-slate-800">{run.task}</span>
                      <Badge variant={statusVariant(run.status)}>{run.status}</Badge>
                    </div>
                    <p className="mt-2 line-clamp-2 text-xs text-slate-500">{run.summary || run.errorMessage || run.id}</p>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
