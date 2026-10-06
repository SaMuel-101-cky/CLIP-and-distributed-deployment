from statistics import fmean, pstdev


def summarize(samples):
    measured = [sample for sample in samples if not sample["warmup"]]
    durations = sorted(float(sample["total_ms"]) for sample in measured if sample["success"])
    count = len(durations)
    total = len(measured)
    percentile = lambda quantile: durations[min(count - 1, int((count - 1) * quantile))] if count else None
    return {
        "measured_success_count": count,
        "success_rate": (count / total * 100) if total else 0.0,
        "mean_total_ms": fmean(durations) if durations else None,
        "p50_total_ms": percentile(.5),
        "p95_total_ms": percentile(.95),
        "stddev_total_ms": pstdev(durations) if count > 1 else 0.0,
        "throughput_per_second": (1000 / fmean(durations)) if durations else 0.0,
    }
