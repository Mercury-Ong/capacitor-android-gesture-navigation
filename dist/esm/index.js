import { registerPlugin } from "@capacitor/core";
const web = () => import("./web").then((m) => new m.GestureNavigationWeb());
export const GestureNavigation = registerPlugin("GestureNavigation", { web, ios: web });
export * from "./definitions";
