"""한국관광공사 반려동물 동반여행 데이터를 앱 내장용 파일로 변환한다.

입력 (docs/data/, API 응답 원본):
  places_list.json   목록 조회 응답 전체
  pet_details.jsonl  장소별 반려동물 동반 정보 (한 줄에 한 곳)
출력:
  app/src/main/assets/places.json

- 반려동물 동반 정보가 있는 장소만 넣는다 (쇼핑 분류는 동반 정보를 받지 않았으므로 빠진다).
- 이미지 주소는 넣지 않는다 (오프라인에서 못 쓰고 저작권 조건이 따로 있다).
- 인증키는 이 스크립트에서 쓰지 않는다. 데이터 수집은 별도로 한다.

사용: python tools/build_places.py
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "data")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "places.json")

# 콘텐츠 유형 → 앱 분류
TYPE = {"12": "ATTRACTION", "14": "LEISURE", "28": "LEISURE", "32": "STAY", "39": "FOOD"}


def clean(text):
    """줄바꿈·연속 공백을 정리한다. '없음' 은 빈 값으로 본다."""
    t = re.sub(r"\s+", " ", (text or "").replace("\r", " ").replace("\n", " ")).strip()
    return "" if t in ("없음", "-", "해당없음") else t


def main():
    with io.open(os.path.join(SRC, "places_list.json"), encoding="utf-8") as f:
        items = json.load(f)["response"]["body"]["items"]["item"]
    by_id = {i["contentid"]: i for i in items}

    out, skipped = [], 0
    with io.open(os.path.join(SRC, "pet_details.jsonl"), encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            pet = json.loads(line)
            item = by_id.get(pet["contentid"])
            kind = TYPE.get(item["contenttypeid"]) if item else None
            if not item or not kind or not item.get("mapx") or not item.get("mapy"):
                skipped += 1
                continue
            address = " ".join(x for x in (clean(item.get("addr1")), clean(item.get("addr2"))) if x)
            out.append({
                "id": item["contentid"],
                "name": clean(item["title"]),
                "type": kind,
                "lat": round(float(item["mapy"]), 6),
                "lng": round(float(item["mapx"]), 6),
                "address": address,
                "tel": clean(item.get("tel")),
                # 반려동물 동반 정보
                "petArea": clean(pet.get("acmpyTypeCd")),        # 전구역 / 일부구역 동반가능
                "petWho": clean(pet.get("acmpyPsblCpam")),       # 동반 가능 동물·크기
                "petNeed": clean(pet.get("acmpyNeedMtr")),       # 필요 사항 (목줄 등)
                "petNote": clean(pet.get("etcAcmpyInfo")),       # 유의사항
                "petFacility": clean(pet.get("relaPosesFclty")), # 보유 시설
                "petSupply": clean(pet.get("relaFrnshPrdlst")),  # 비치 품목
            })

    out.sort(key=lambda p: p["id"])
    with io.open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    kinds = {}
    for p in out:
        kinds[p["type"]] = kinds.get(p["type"], 0) + 1
    print("wrote %d places (%d skipped) -> %s (%.0f KB)" % (len(out), skipped, os.path.relpath(OUT, ROOT), os.path.getsize(OUT) / 1024))
    print(kinds)


if __name__ == "__main__":
    sys.exit(main())
