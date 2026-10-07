import { exportUrl } from "../api";
import type { BenchmarkRun } from "../types";

type RunOverviewProps = {
  run: BenchmarkRun | null;
};

function formatMilliseconds(value: number | null | undefined): string {
  return typeof value === "number" ? `${value.toFixed(2)} ms` : "—";
}

export function RunOverview({ run }: RunOverviewProps) {
  const summary = run?.summary;
  const status = run?.status ?? "等待运行";

  return (
    <section className="run-overview" aria-live="polite">
      <div className="run-status-line">
        <div>
          <p className="eyebrow">运行状态</p>
          <h2>{status}</h2>
        </div>
        <div className="sample-counter">
          <strong>{String(run?.sampleCount ?? 0).padStart(2, "0")}</strong>
          <span>样本</span>
        </div>
      </div>

      <div className="metric-grid">
        <Metric label="平均耗时" value={formatMilliseconds(summary?.mean_total_ms)} />
        <Metric label="P95 耗时" value={formatMilliseconds(summary?.p95_total_ms)} />
        <Metric label="成功率" value={typeof summary?.success_rate === "number" ? `${summary.success_rate.toFixed(1)}%` : "—"} />
        <Metric label="吞吐" value={typeof summary?.throughput_per_second === "number" ? `${summary.throughput_per_second.toFixed(2)} batch/s` : "—"} />
      </div>

      {run?.status === "COMPLETED" && (
        <a className="export-link" href={exportUrl(run.runId)}>下载性能 CSV</a>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
