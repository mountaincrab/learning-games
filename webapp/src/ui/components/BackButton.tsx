/** Round, kid-friendly back button — same glyph treatment as the Android app. */
export default function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      aria-label="Back"
      className="flex h-[52px] w-[52px] items-center justify-center rounded-full bg-white/90 text-[26px] font-bold text-[#2B2B2B] shadow-md transition-transform active:scale-95"
    >
      ←
    </button>
  )
}
