import { imageUrl } from "../api";
import type { ImageItem } from "../types";

type ImageGalleryProps = {
  images: ImageItem[];
  selectedNames: string[];
  disabled: boolean;
  onToggle: (imageName: string) => void;
  onRefresh: () => void;
};

export function ImageGallery({
  images,
  selectedNames,
  disabled,
  onToggle,
  onRefresh,
}: ImageGalleryProps) {
  const selected = new Set(selectedNames);

  return (
    <section className="image-picker" aria-labelledby="image-picker-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">测试图片</p>
          <h2 id="image-picker-title">photo_resources/test</h2>
        </div>
        <button className="text-button" type="button" onClick={onRefresh} disabled={disabled}>
          刷新目录
        </button>
      </div>

      {images.length === 0 ? (
        <p className="empty-state">目录中暂无可用图片。请先把图片复制到固定测试目录。</p>
      ) : (
        <div className="image-grid">
          {images.map((image) => {
            const isSelected = selected.has(image.name);
            return (
              <button
                className={`image-card${isSelected ? " image-card--selected" : ""}`}
                type="button"
                key={image.name}
                onClick={() => onToggle(image.name)}
                aria-pressed={isSelected}
                disabled={disabled}
              >
                <img src={imageUrl(image.name)} alt={image.name} />
                <span title={image.name}>{image.name}</span>
                {isSelected && <b>已选择</b>}
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}
