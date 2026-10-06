# Local offload benchmark console

Start MySQL and the Java backend locally, apply `CLIP_backend/migrations/2026-10-06_add_benchmark_runs.sql`, then start the remote model service with the same `OFFLOAD_TOKEN` as the local client. Never expose the Java backend, Chroma, benchmark service, or metrics endpoint publicly.

```powershell
cd D:\CLIP\CLIP_model
D:\Anaconda\envs\CLIP\python.exe client.py
cd D:\CLIP\CLIP_frontend
npm install
npm run dev
```

The model service owns both inference and benchmark routes on port `5000`; the Vite UI binds to `127.0.0.1` and proxies `/api` to that local model service. Put the test images under `D:\CLIP\photo_resources\test`; the UI lists only files from that directory and does not accept arbitrary local paths. Use identical images, queries, batch size, warmups and measured-run counts for every mode. Warmups remain in exports for audit but are omitted from aggregates. A configuration with failed correctness checks is invalid even if it has a smaller mean latency.

For every mode, report mean/P50/P95 total latency, throughput, success rate, payload sizes, remote inference time, and the network-plus-queue estimate. Describe remote GPU acceleration separately from end-to-end acceleration after transport costs.
