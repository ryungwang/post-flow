import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/components/theme-provider";

export function ThemeToggle() {
  const { resolved, setTheme } = useTheme();
  const next = resolved === "dark" ? "light" : "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={`${next} 모드로 전환`}
      title={`${next === "dark" ? "다크" : "라이트"} 모드`}
      onClick={() => setTheme(next)}
    >
      {resolved === "dark" ? <Sun /> : <Moon />}
    </Button>
  );
}
