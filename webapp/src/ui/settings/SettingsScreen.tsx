import { ReactNode, useState } from 'react'
import BackButton from '../components/BackButton'

/** Settings screen: sound toggle (state only for now, matching Android) + version. */
export default function SettingsScreen({ onBack }: { onBack: () => void }) {
  const [soundOn, setSoundOn] = useState(true)

  return (
    <div
      className="h-full w-full overflow-y-auto px-6 py-5"
      style={{ background: 'linear-gradient(to bottom, #2E8BC0, #145DA0)' }}
    >
      <div className="flex items-center gap-4">
        <BackButton onClick={onBack} />
        <h1 className="text-[26px] font-bold text-white">Settings</h1>
      </div>

      <div className="mt-6 flex flex-col gap-4">
        <SettingsCard>
          <div className="flex items-center justify-between p-5">
            <span className="text-lg text-[#2B2B2B]">Sound</span>
            <button
              role="switch"
              aria-checked={soundOn}
              aria-label="Sound"
              onClick={() => setSoundOn(!soundOn)}
              className={`relative h-8 w-14 rounded-full transition-colors ${
                soundOn ? 'bg-[#145DA0]' : 'bg-gray-300'
              }`}
            >
              <span
                className={`absolute top-1 h-6 w-6 rounded-full bg-white shadow transition-all ${
                  soundOn ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>
        </SettingsCard>

        <SettingsCard>
          <div className="p-5">
            <p className="text-lg font-bold text-[#2B2B2B]">Learning Games</p>
            <p className="mt-1 text-sm text-[#2B2B2B]/70">Version {__APP_VERSION__} (web)</p>
          </div>
        </SettingsCard>
      </div>
    </div>
  )
}

function SettingsCard({ children }: { children: ReactNode }) {
  return <div className="w-full rounded-xl bg-white/95 shadow">{children}</div>
}
