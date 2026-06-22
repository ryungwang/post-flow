import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

type ConfirmOptions = {
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  destructive?: boolean;
};

type Resolver = (ok: boolean) => void;

const ConfirmContext = createContext<(opts: ConfirmOptions) => Promise<boolean>>(() => Promise.resolve(false));

/** Imperative custom confirm — `const confirm = useConfirm(); if (await confirm({...})) {...}`. */
export function useConfirm() {
  return useContext(ConfirmContext);
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [opts, setOpts] = useState<ConfirmOptions | null>(null);
  const [resolver, setResolver] = useState<{ fn: Resolver } | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    setOpts(options);
    return new Promise<boolean>((resolve) => setResolver({ fn: resolve }));
  }, []);

  const close = (ok: boolean) => {
    resolver?.fn(ok);
    setResolver(null);
    setOpts(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={!!opts} onOpenChange={(o) => { if (!o) close(false); }}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{opts?.title}</DialogTitle>
            {opts?.description && <DialogDescription>{opts.description}</DialogDescription>}
          </DialogHeader>
          <DialogFooter className="mt-2 gap-2 sm:gap-2">
            <Button variant="outline" onClick={() => close(false)}>{opts?.cancelText ?? "취소"}</Button>
            <Button variant={opts?.destructive ? "destructive" : "default"} onClick={() => close(true)}>
              {opts?.confirmText ?? "확인"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </ConfirmContext.Provider>
  );
}
