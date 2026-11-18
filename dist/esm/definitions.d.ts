import type { PluginListenerHandle } from "@capacitor/core";
/** How the user navigates: gestures, or 2- or 3-button navigation. */
export type NavigationMode = "gesture" | "buttons";
/** Tone of the screen under a system bar. */
export type BarTone = "dark" | "light";
export interface ToneChangeEvent {
    /** Tone of the screen under the status bar. */
    top: BarTone;
    /** Tone of the screen just above the navigation bar. */
    bottom: BarTone;
    /** The dominant colour just above the navigation bar, as `#RRGGBB`. */
    bottomColor: string;
}
export interface AutoStyleOptions {
    /** How often the screen edges are sampled, in ms. Default 250, minimum 100. */
    interval?: number;
}
export interface GestureNavigationPlugin {
    /** Current navigation mode. Web and iOS resolve `gesture`. */
    getMode(): Promise<{
        mode: NavigationMode;
    }>;
    /** Navigation button colour: `dark` over light backgrounds, `light` over dark ones. */
    setStyle(options: {
        buttons: "dark" | "light";
    }): Promise<void>;
    /**
     * Samples the app's pixels under the status bar and just above the
     * navigation bar, and styles both bars to stay readable. Emits `toneChange`.
     */
    startAutoStyle(options?: AutoStyleOptions): Promise<void>;
    stopAutoStyle(): Promise<void>;
    /** Fired when the user switches navigation mode in system settings. */
    addListener(event: "modeChange", listener: (event: {
        mode: NavigationMode;
    }) => void): Promise<PluginListenerHandle>;
    /** Fired by auto style when a bar's tone or the bottom colour changes. */
    addListener(event: "toneChange", listener: (event: ToneChangeEvent) => void): Promise<PluginListenerHandle>;
    removeAllListeners(): Promise<void>;
}
