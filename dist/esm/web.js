import { WebPlugin } from "@capacitor/core";
/** Web: no system bars to manage, so every call is a no-op. */
export class GestureNavigationWeb extends WebPlugin {
    async getMode() {
        return { mode: "gesture" };
    }
    async setStyle() { }
    async startAutoStyle() { }
    async stopAutoStyle() { }
}
