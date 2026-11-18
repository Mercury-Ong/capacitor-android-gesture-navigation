import { WebPlugin } from "@capacitor/core";
/** Web and iOS: no navigation buttons to manage, so every call is a no-op. */
export class GestureNavigationWeb extends WebPlugin {
    async getMode() {
        return { mode: "gesture" };
    }
    async setStyle() { }
    async startAutoStyle() { }
    async stopAutoStyle() { }
}
