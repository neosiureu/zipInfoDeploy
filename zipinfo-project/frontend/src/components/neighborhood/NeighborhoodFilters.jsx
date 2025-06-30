// src/components/neighborhood/NeighborhoodFilters.jsx
import React from "react";
import arrowDown from "../../assets/arrow-down.svg";
import { CITY, TOWN } from "../common/Gonggong";
import "../../css/neighborhood/NeighborhoodBoard.css";

export default function NeighborhoodFilters({
  selectedCity,
  selectedTown,
  selectedSubject,
  onCityChange,
  onTownChange,
  onSubjectChange,
}) {
  const townsForCity =
    selectedCity === -1
      ? []
      : TOWN.filter((t) => t.code === Number(selectedCity));

  return (
    <div className="nb-filters">
      <div className="nb-select-wrap">
        <select
          className="nb-select"
          value={selectedCity}
          onChange={onCityChange}
        >
          <option value={-1}>시/도</option>
          {CITY.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </select>
        <img className="arrow-icon" src={arrowDown} alt="시/도" />
      </div>

      <div className="select-wrap">
        <select
          className="nb-select"
          value={selectedTown}
          onChange={onTownChange}
          disabled={selectedCity === -1}
        >
          <option value={-1}>시/군/구</option>
          {townsForCity.map((t) => (
            <option key={t.fullcode} value={t.fullcode}>
              {t.name}
            </option>
          ))}
        </select>
        <img className="arrow-icon" src={arrowDown} alt="시/군/구" />
      </div>

      <div className="select-wrap">
        <select
          className="nb-select nb-select-wide"
          value={selectedSubject}
          onChange={onSubjectChange}
        >
          <option value={-1}>주제 분류</option>
          <option value="Q">질문</option>
          <option value="R">리뷰</option>
          <option value="E">기타</option>
        </select>
        <img className="arrow-icon" src={arrowDown} alt="주제" />
      </div>
    </div>
  );
}
