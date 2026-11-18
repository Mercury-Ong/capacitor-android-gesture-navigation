import { WebPlugin } from "@capacitor/core";
import type { GestureNavigationPlugin, NavigationMode } from "./definitions";

/** Web and iOS: no navigation buttons to manage, so every call is a no-op. */
export class GestureNavigationWeb
  extends WebPlugin
  implements GestureNavigationPlugin
{
  async getMode(): Promise<{ mode: NavigationMode }> {
    return { mode: "gesture" };
  }

  async setStyle(): Promise<void> {}

  async startAutoStyle(): Promise<void> {}

  async stopAutoStyle(): Promise<void> {}
}
