import { registerPlugin } from "@capacitor/core";
import type { GestureNavigationPlugin } from "./definitions";

const web = () => import("./web").then((m) => new m.GestureNavigationWeb());

export const GestureNavigation = registerPlugin<GestureNavigationPlugin>(
  "GestureNavigation",
  { web }
);

export * from "./definitions";
