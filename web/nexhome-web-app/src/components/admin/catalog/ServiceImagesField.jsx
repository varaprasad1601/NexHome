import { ImagePlus, Star, X } from 'lucide-react';
import { useRef, useState } from 'react';

export const MAX_SERVICE_IMAGES = 5;
const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * A service's images: a drop zone (drag and drop, or click to pick; JPG, PNG or WebP up to 5 MB, at most 5 images)
 * and below it the previews of the stored images and the newly picked ones (tagged New until saved), each with a
 * remove button. The page uploads new images and deletes removed ones when it saves.
 *
 * @param images [{ key, url, name, isNew }] in display order
 * @param onAdd called with the accepted File objects
 * @param onRemove called with an image's key
 * @param onSetCover called with an image's key to make it the cover (moved first); the star on each other image
 * @param canRemoveStored whether stored images can be removed (CATALOG_MANAGE delete); new ones always can
 */
export default function ServiceImagesField({ images, onAdd, onRemove, onSetCover, canRemoveStored, disabled = false }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState('');
  const room = MAX_SERVICE_IMAGES - images.length;
  const canAdd = !disabled && room > 0;

  const accept = (fileList) => {
    const files = Array.from(fileList ?? []);
    if (files.length === 0) return;
    const accepted = [];
    let problem = '';
    for (const file of files) {
      if (!ACCEPTED_TYPES.includes(file.type)) problem = `${file.name} is not a JPG, PNG or WebP image.`;
      else if (file.size > MAX_BYTES) problem = `${file.name} is larger than 5 MB.`;
      else if (accepted.length >= room) problem = `A service can have at most ${MAX_SERVICE_IMAGES} images.`;
      else accepted.push(file);
    }
    setError(problem);
    if (accepted.length) onAdd(accepted);
  };

  return (
    <div>
      <div
        role="button"
        tabIndex={canAdd ? 0 : -1}
        aria-disabled={!canAdd}
        aria-label="Upload service images"
        onClick={() => canAdd && inputRef.current?.click()}
        onKeyDown={(e) => {
          if (canAdd && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            inputRef.current?.click();
          }
        }}
        onDragOver={(e) => {
          if (!canAdd) return;
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (canAdd) accept(e.dataTransfer.files);
        }}
        className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-4 py-7 text-center outline-none transition focus-visible:ring-2 focus-visible:ring-[var(--blue)]/20 ${
          !canAdd
            ? 'cursor-not-allowed border-[var(--line)] bg-[var(--light)] opacity-70'
            : dragging
              ? 'border-[var(--blue)] bg-[#EDF6FD]'
              : 'border-[#C7D6E6] bg-[#FAFCFE] hover:border-[var(--blue)] hover:bg-[#F4F9FE]'
        }`}
      >
        <span className="mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-[#EDF6FD] text-[var(--blue)]">
          <ImagePlus size={20} />
        </span>
        <span className="text-sm font-bold text-[var(--ink)]">
          {room > 0 ? 'Drag & drop images here' : 'Image limit reached'}
        </span>
        {room > 0 && <span className="mt-0.5 text-[0.8125rem] font-bold text-[var(--blue)]">or click to upload</span>}
        <span className="mt-2 text-xs text-[var(--muted)]">
          JPG, PNG or WebP up to 5 MB (max {MAX_SERVICE_IMAGES} images)
        </span>
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={ACCEPTED_TYPES.join(',')}
          className="hidden"
          onChange={(e) => {
            accept(e.target.files);
            e.target.value = '';
          }}
        />
      </div>
      {error && <div className="mt-1.5 text-xs font-bold text-[#C53030]">{error}</div>}

      <div className="mt-3 flex items-center justify-between text-xs font-bold text-[var(--muted)]">
        <span>Images</span>
        <span>
          {images.length} / {MAX_SERVICE_IMAGES}
        </span>
      </div>
      {images.length === 0 ? (
        <p className="mt-2 rounded-lg bg-[var(--light)] px-3 py-3 text-center text-xs text-[var(--muted)]">
          No images yet. The first image is the service's cover.
        </p>
      ) : (
        <ul className="mt-2 grid list-none grid-cols-3 gap-2 p-0 sm:grid-cols-5">
          {images.map((image, index) => {
            const removable = !disabled && (image.isNew || canRemoveStored);
            return (
              <li
                key={image.key}
                className="group relative aspect-square overflow-hidden rounded-lg border border-[var(--line)] bg-[var(--light)]"
              >
                <img src={image.url} alt={image.name} title={image.name} className="h-full w-full object-cover" />
                {index === 0 ? (
                  <span className="absolute bottom-1 left-1 flex items-center gap-0.5 rounded bg-[var(--navy)]/85 px-1.5 py-0.5 text-[0.625rem] font-bold text-white">
                    <Star size={9} fill="currentColor" strokeWidth={0} />
                    Cover
                  </span>
                ) : (
                  !disabled && (
                    <button
                      type="button"
                      onClick={() => onSetCover(image.key)}
                      title="Set as cover image"
                      aria-label={`Set ${image.name} as the cover image`}
                      className="absolute bottom-1 left-1 flex items-center gap-0.5 rounded bg-white/90 px-1.5 py-0.5 text-[0.625rem] font-bold text-[var(--navy)] opacity-0 shadow transition hover:bg-[var(--navy)] hover:text-white focus-visible:opacity-100 group-hover:opacity-100"
                    >
                      <Star size={9} strokeWidth={2.6} />
                      Set cover
                    </button>
                  )
                )}
                {image.isNew && (
                  <span className="absolute left-1 top-1 rounded bg-[var(--green)] px-1.5 py-0.5 text-[0.625rem] font-bold text-white">
                    New
                  </span>
                )}
                {removable && (
                  <button
                    type="button"
                    onClick={() => onRemove(image.key)}
                    aria-label={`Remove ${image.name}`}
                    className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-white/90 text-[var(--ink)] shadow hover:bg-[#C53030] hover:text-white"
                  >
                    <X size={13} strokeWidth={2.6} />
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
