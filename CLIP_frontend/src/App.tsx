import { useCallback, useEffect, useMemo, useState } from "react";

import { fetchExperiment, fetchImages, startExperiment } from "./api";
import { ExperimentControls } from "./components/ExperimentControls";
import { ImageGallery } from "./components/ImageGallery";
import { MatchResults } from "./components/MatchResults";
import { RunOverview } from "./components/RunOverview";
import type { BenchmarkMode, BenchmarkRun, ImageItem } from "./types";

const ACTIVE_STATUSES = new Set(["PENDING", "RUNNING"]);

export function App() {
  const [images, setImages] = useState<ImageItem[]>([]);
  const [selectedNames, setSelectedNames] = useState<string[]>([]);
  const [mode, setMode] = useState<BenchmarkMode>("LOCAL_ALL");
  const [queries, setQueries] = useState("");
  const [remoteHost, setRemoteHost] = useState("");
  const [run, setRun] = useState<BenchmarkRun | null>(null);
  const [error, setError] = useState("");
  const [loadingImages, setLoadingImages] = useState(false);

  const running = Boolean(run && ACTIVE_STATUSES.has(run.status));
  const queryList = useMemo(
    () => queries.split(/\r?\n/).map((query) => query.trim()).filter(Boolean),
    [queries],
  );

  const loadImages = useCallback(async () => {
    setLoadingImages(true);
    try {
      const availableImages = await fetchImages();
      setImages(availableImages);
      setSelectedNames((current) => current.filter((name) => availableImages.some((image) => image.name === name)));
      setError("");
    } catch {
      setError("无法连接模型服务。请确认 CLIP_model/client.py 正在运行于 127.0.0.1:5000。");
    } finally {
      setLoadingImages(false);
    }
  }, []);

  useEffect(() => {
    void loadImages();
  }, [loadImages]);

  useEffect(() => {
    if (!run || !ACTIVE_STATUSES.has(run.status)) {
      return undefined;
    }

    const poll = async () => {
      try {
        setRun(await fetchExperiment(run.runId));
      } catch {
        setError("无法读取运行状态。请检查模型服务日志。");
      }
    };
    void poll();
    const intervalId = window.setInterval(() => void poll(), 800);
    return () => window.clearInterval(intervalId);
  }, [run]);

  function toggleImage(imageName: string) {
    setSelectedNames((current) => (
      current.includes(imageName)
        ? current.filter((name) => name !== imageName)
        : [...current, imageName]
    ));
  }

  async function runExperiment() {
    if (selectedNames.length === 0) {
      setError("请至少选择一张测试图片。");
      return;
    }
    if (queryList.length === 0) {
      setError("请至少输入一条文本描述。");
      return;
    }

    setError("");
    try {
      const runId = await startExperiment({
        mode,
        imageNames: selectedNames,
        queries: queryList,
        remoteHost: remoteHost.trim() || undefined,
      });
      setRun({ runId, status: "PENDING", sampleCount: 0, summary: {}, results: [] });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "无法创建本次运行。");
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">LOCAL / OFFLOAD / RETRIEVAL</p>
          <h1>CLIP</h1>
        </div>
        <p>选择本地目录中的图片，比较文本匹配结果与端到端性能。</p>
      </header>

      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="workspace">
        <ExperimentControls
          mode={mode}
          queries={queries}
          remoteHost={remoteHost}
          selectedCount={selectedNames.length}
          running={running}
          onModeChange={setMode}
          onQueriesChange={setQueries}
          onRemoteHostChange={setRemoteHost}
          onRun={() => void runExperiment()}
        />

        <div className="content-column">
          <RunOverview run={run} />
          <ImageGallery
            images={images}
            selectedNames={selectedNames}
            disabled={running || loadingImages}
            onToggle={toggleImage}
            onRefresh={() => void loadImages()}
          />
          <MatchResults results={run?.results ?? []} complete={run?.status === "COMPLETED"} />
        </div>
      </div>
    </main>
  );
}
