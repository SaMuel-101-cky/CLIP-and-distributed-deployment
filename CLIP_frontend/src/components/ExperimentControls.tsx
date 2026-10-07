import type { ChangeEvent } from "react";

import { MODES, type BenchmarkMode } from "../types";

type ExperimentControlsProps = {
  mode: BenchmarkMode;
  queries: string;
  remoteHost: string;
  selectedCount: number;
  running: boolean;
  onModeChange: (mode: BenchmarkMode) => void;
  onQueriesChange: (queries: string) => void;
  onRemoteHostChange: (remoteHost: string) => void;
  onRun: () => void;
};

export function ExperimentControls({
  mode,
  queries,
  remoteHost,
  selectedCount,
  running,
  onModeChange,
  onQueriesChange,
  onRemoteHostChange,
  onRun,
}: ExperimentControlsProps) {
  const selectedMode = MODES.find((item) => item.value === mode)!;

  function handleModeChange(event: ChangeEvent<HTMLSelectElement>) {
    onModeChange(event.target.value as BenchmarkMode);
  }

  return (
    <aside className="control-panel">
      <p className="eyebrow">运行配置</p>
      <h2>准备一次检索</h2>

      <label htmlFor="mode">推理位置</label>
      <select id="mode" value={mode} onChange={handleModeChange} disabled={running}>
        {MODES.map((item) => (
          <option value={item.value} key={item.value}>{item.label}</option>
        ))}
      </select>
      <p className="field-hint">{selectedMode.hint}</p>

      <label htmlFor="queries">文本描述</label>
      <textarea
        id="queries"
        value={queries}
        onChange={(event) => onQueriesChange(event.target.value)}
        placeholder={"一行一条，例如：\na cat\na dog"}
        disabled={running}
      />
      <p className="field-hint">会对每张已选图片，为每条文本计算并排序。</p>

      <label htmlFor="remote-host">AutoDL 地址（可选）</label>
      <input
        id="remote-host"
        value={remoteHost}
        onChange={(event) => onRemoteHostChange(event.target.value)}
        placeholder="远程模式可填写域名或 IP"
        disabled={running}
      />
      <p className="field-hint">留空时使用模型服务的 SERVER_IP 配置。</p>

      <div className="selection-summary">
        <span>已选图片</span>
        <strong>{selectedCount}</strong>
      </div>
      <button className="run-button" type="button" onClick={onRun} disabled={running}>
        {running ? "正在推理…" : "开始图文匹配"}
      </button>
      <p className="benchmark-note">每次运行包含 1 次预热与 10 次正式测量；结果使用第一轮成功的正式测量。</p>
    </aside>
  );
}
