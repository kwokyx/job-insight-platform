from __future__ import annotations

import json

from app.ml.salary_model import train_and_save


def main() -> None:
    result = train_and_save()
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
