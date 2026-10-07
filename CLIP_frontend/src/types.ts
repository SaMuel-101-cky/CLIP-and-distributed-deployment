export type BenchmarkMode =
  | "LOCAL_ALL"
  | "REMOTE_ALL"
  | "TEXT_LOCAL_IMAGE_REMOTE"
  | "TEXT_REMOTE_IMAGE_LOCAL";

export type ImageItem = {
  name: string;
};

export type TextMatch = {
  query: string;
  score: number;
  probability: number;
  rank: number;
};

export type ImageMatchResult = {
  imageName: string;
  matches: TextMatch[];
};

export type RunSummary = {
  mean_total_ms?: number | null;
  p50_total_ms?: number | null;
  p95_total_ms?: number | null;
  success_rate?: number | null;
  throughput_per_second?: number | null;
};

export type BenchmarkRun = {
  runId: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "CANCELLED" | string;
  sampleCount: number;
  summary: RunSummary;
  results: ImageMatchResult[];
};

export type StartExperimentInput = {
  mode: BenchmarkMode;
  imageNames: string[];
  queries: string[];
  remoteHost?: string;
};

export const MODES: Array<{ value: BenchmarkMode; label: string; hint: string }> = [
  { value: "LOCAL_ALL", label: "全部本地", hint: "图像与文本均在本机推理" },
  { value: "REMOTE_ALL", label: "全部远程", hint: "图像与文本编码都卸载到远端" },
  { value: "TEXT_LOCAL_IMAGE_REMOTE", label: "图片远程", hint: "文本本地、图片编码远程" },
  { value: "TEXT_REMOTE_IMAGE_LOCAL", label: "文本远程", hint: "图片本地、文本编码远程" },
];
