import { imageUrl } from "../api";
import type { ImageMatchResult } from "../types";

type MatchResultsProps = {
  results: ImageMatchResult[];
  complete: boolean;
};

export function MatchResults({ results, complete }: MatchResultsProps) {
  return (
    <section className="match-results" aria-labelledby="match-results-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">图文匹配结果</p>
          <h2 id="match-results-title">按相似度排序</h2>
        </div>
        {results.length > 0 && <span className="result-badge">{results.length} 张图片</span>}
      </div>

      {!complete && <p className="empty-state">运行完成后，这里会显示每张图片的文本匹配排名。</p>}
      {complete && results.length === 0 && <p className="empty-state">本次正式测量没有产生可展示的匹配结果。</p>}

      <div className="result-grid">
        {results.map((result) => (
          <article className="result-card" key={result.imageName}>
            <img src={imageUrl(result.imageName)} alt={result.imageName} />
            <div className="result-card__body">
              <h3 title={result.imageName}>{result.imageName}</h3>
              <ol>
                {result.matches.map((match) => (
                  <li key={`${match.rank}-${match.query}`}>
                    <span className="rank">{match.rank}</span>
                    <span className="match-query">{match.query}</span>
                    <strong>{match.probability.toFixed(1)}%</strong>
                    <small>logit {match.score.toFixed(2)}</small>
                  </li>
                ))}
              </ol>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
