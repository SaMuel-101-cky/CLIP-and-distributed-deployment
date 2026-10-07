import type { BenchmarkRun, ImageItem, StartExperimentInput } from "./types";

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error ?? `请求失败（HTTP ${response.status}）`);
  }
  return response.json() as Promise<T>;
}

export async function fetchImages(): Promise<ImageItem[]> {
  const response = await requestJson<{ images: ImageItem[] }>("/api/images");
  return response.images;
}

export async function startExperiment(input: StartExperimentInput): Promise<string> {
  const response = await requestJson<{ runId: string }>("/api/experiments", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...input,
      batchSize: 1,
      warmupRuns: 1,
      measuredRuns: 10,
    }),
  });
  return response.runId;
}

export function fetchExperiment(runId: string): Promise<BenchmarkRun> {
  return requestJson<BenchmarkRun>(`/api/experiments/${encodeURIComponent(runId)}`);
}

export function imageUrl(imageName: string): string {
  return `/api/images/${encodeURIComponent(imageName)}`;
}

export function exportUrl(runId: string): string {
  return `/api/experiments/${encodeURIComponent(runId)}/export.csv`;
}
