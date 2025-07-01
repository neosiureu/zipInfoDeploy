// src/components/neighborhood/NeighborhoodFilters.jsx
import arrowDown from "../../assets/arrow-down.svg";
import { CITY, TOWN } from "../common/Gonggong";
import "../../css/neighborhood/NeighborhoodBoard.css";

export default function NeighborhoodFilters({
  selectedCity,
  selectedTown,
  onCityChange,
  onTownChange,
}) {
  const townsForCity =
    selectedCity === -1
      ? []
      : TOWN.filter((t) => t.code === Number(selectedCity));

  return (
    <div className="nb-filters">
      <div className="nb-select-wrap">
        <select
          className="member-location-select"
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
          className="member-location-select"
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
    </div>
  );
}
