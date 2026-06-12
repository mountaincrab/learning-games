/// <reference types="vite/client" />

/** Injected at build time from `git describe` (see vite.config.ts). */
declare const __APP_VERSION__: string

/** Importing an `.ogg` asset yields its resolved URL (Vite bundles & hashes it). */
declare module '*.ogg' {
  const src: string
  export default src
}
