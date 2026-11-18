import { WebPlugin } from "@capacitor/core";
import type { GestureNavigationPlugin, NavigationMode } from "./definitions";
/** Web: no system bars to manage, so every call is a no-op. */
export declare class GestureNavigationWeb extends WebPlugin implements GestureNavigationPlugin {
    getMode(): Promise<{
        mode: NavigationMode;
    }>;
    setStyle(): Promise<void>;
    startAutoStyle(): Promise<void>;
    stopAutoStyle(): Promise<void>;
}
