import { useState } from "react";
import { Star } from "lucide-react";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function RatingModal({ open, onSubmit }) {
  const [stars, setStars] = useState(5);
  const [hover, setHover] = useState(0);

  return (
    <Dialog open={open} onClose={() => onSubmit(stars)} dismissable={false}>
      <div className="text-center">
        <h3 className="text-lg font-semibold">Rate your ride</h3>
        <p className="mt-1 text-sm text-muted-foreground">How was your trip?</p>
        <div className="my-6 flex justify-center gap-1">
          {[1, 2, 3, 4, 5].map((s) => (
            <button
              key={s}
              onMouseEnter={() => setHover(s)}
              onMouseLeave={() => setHover(0)}
              onClick={() => setStars(s)}
            >
              <Star
                className={cn(
                  "h-9 w-9 transition-colors",
                  (hover || stars) >= s
                    ? "fill-warning text-warning"
                    : "text-muted-foreground",
                )}
              />
            </button>
          ))}
        </div>
        <Button className="w-full" onClick={() => onSubmit(stars)}>
          Submit rating
        </Button>
      </div>
    </Dialog>
  );
}
