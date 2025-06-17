import "../css/Main.css";

import search from "../assets/search-icon.svg";
import apart from "../assets/apart-icon.svg";
import house from "../assets/house-villa-icon.svg";
import officetel from "../assets/officetel-icon.svg";
import sale from "../assets/sale-icon.svg";

import banner from "../assets/banner.svg";

import main01 from "../assets/main-thumbnail-01.svg";
import main02 from "../assets/main-thumbnail-02.svg";
import main03 from "../assets/main-thumbnail-03.svg";
import main04 from "../assets/main-thumbnail-04.svg";

import agent from "../assets/agent-icon.svg";

import saleMain01 from "../assets/sale-thumbnail-01.svg";
import saleMain02 from "../assets/sale-thumbnail-02.svg";
import saleMain03 from "../assets/sale-thumbnail-03.svg";
import saleMain04 from "../assets/sale-thumbnail-04.svg";

const Main = () => {
  return (
    <main>
      <section className="main-visual">
        <div className="main-visual-bg"></div>
        <div className="main-visual-content">
          <h1>
            <span>ZIPinfo와 함께하는 내 집 마련의 여정</span>
          </h1>
          <p className="subtitle">
            당신의 삶이 머무를 공간을 위해 믿을 수 있는 정보로 함께
            찾아드립니다.
          </p>
          <div className="search-bar">
            <img src={search} alt="검색 아이콘" className="search-icon" />
            <input type="text" placeholder="검색어를 입력하세요" />
          </div>
          <div className="main-icons">
            <button className="apart-icons">
              <img src={apart} alt="아파트 아이콘 이미지" />
            </button>
            <button className="house-icons">
              <img src={house} alt="주택/빌라 아이콘 이미지" />
            </button>
            <button className="officetel-icons">
              <img src={officetel} alt="오피스텔 아이콘 이미지" />
            </button>
            <button className="sale-icons">
              <img src={sale} alt="분양 아이콘 이미지" />
            </button>
          </div>
        </div>
      </section>

      <div className="banner">
        <img src={banner} alt="배너광고 이미지" />
      </div>

      <section className="section-main">
        {/* 인기 단지 */}
        <div className="section-header">
          <h2>요즘 많이 찾는 트렌디한 매력적인 단지들이에요</h2>
          <button className="more-btn">모두 보기</button>
        </div>
        <div className="card-list">
          <div className="card">
            <img src={main01} alt="실거래 집 썸네일 이미지" />
            <div className="card-title">아파트 · 아크로리버파크 105동</div>
            <div className="card-price">
              <span>매매</span> 55억 5,000
            </div>
            <div className="card-desc">
              3/31층 <span>|</span> 114.20㎡ <span>|</span> 관리비 45만원
            </div>
            <div className="card-agent">
              <span>
                <img src={agent} alt="중개사 아이콘" />
              </span>
              바른공인중개사사무소
            </div>
          </div>
          <div className="card">
            <img src={main02} alt="실거래 집 썸네일 이미지" />
            <div className="card-title">아파트 · 나인원한남 108동</div>
            <div className="card-price">
              <span>매매</span> 260억
            </div>
            <div className="card-desc">
              1/5층 <span>|</span> 273㎡ <span>|</span> 관리비 150만원
            </div>
            <div className="card-agent">
              <span>
                <img src={agent} alt="중개사 아이콘" />
              </span>
              바른공인중개사사무소
            </div>
          </div>
          <div className="card">
            <img src={main03} alt="실거래 집 썸네일 이미지" />
            <div className="card-title">아파트 · 판교푸르지오월드마크 2동</div>
            <div className="card-price">
              <span>매매</span> 24억 5,000
            </div>
            <div className="card-desc">
              7/20층 <span>|</span> 143.33㎡ <span>|</span> 관리비 68만원
            </div>
            <div className="card-agent">
              <span>
                <img src={agent} alt="중개사 아이콘" />
              </span>
              바른공인중개사사무소
            </div>
          </div>
          <div className="card">
            <img src={main04} alt="실거래 집 썸네일 이미지" />
            <div className="card-title">아파트 · 래미안퍼스티지 122동</div>
            <div className="card-price">
              <span>매매</span> 75억
            </div>
            <div className="card-desc">
              16/26층 <span>|</span> 198.22㎡ <span>|</span> 관리비 70만원
            </div>
            <div className="card-agent">
              <span>
                <img src={agent} alt="중개사 아이콘" />
              </span>
              바른공인중개사사무소
            </div>
          </div>
        </div>

        {/* 분양 소식 */}
        <div className="sale">
          <div className="section-header">
            <h2>분양 소식을 빠르게 접해보세요</h2>
            <button className="more-btn">모두 보기</button>
          </div>
          <div className="card-list">
            <div className="card-sale">
              <img src={saleMain01} alt="분양 썸네일 이미지" />
              <div className="card-title">
                아파트 · 에스아이팰리스올림픽공원
              </div>
              <div className="card-price">
                <span>분양가</span> 10억 9,000 ~ 11억 4,000
              </div>
              <div className="card-adress">서울시 강동구 성내동 459-3</div>
              <div className="card-area">70.02㎡</div>
            </div>
            <div className="card-sale">
              <img src={saleMain02} alt="분양 썸네일 이미지" />
              <div className="card-title">아파트 · 한신더휴하이엔에듀포레</div>
              <div className="card-price">
                <span>분양가</span> 5억 ~ 6억 8,950
              </div>
              <div className="card-adress">서울시 금천구 시흥동 220-2</div>
              <div className="card-area">62.04㎡ ~ 82.05㎡</div>
            </div>
            <div className="card-sale">
              <img src={saleMain03} alt="분양 썸네일 이미지" />
              <div className="card-title">아파트 · 교산푸르지오더퍼스트</div>
              <div className="card-price">
                <span>분양가</span> 4억 6,778 ~ 5억 7,167
              </div>
              <div className="card-adress">경기도 하남시 천현동 130번지</div>
              <div className="card-area">73.60㎡ ~ 85.12㎡</div>
            </div>
            <div className="card-sale">
              <img src={saleMain04} alt="분양 썸네일 이미지" />
              <div className="card-title">아파트 · 월드메르디앙올림픽파크</div>
              <div className="card-price blue">
                <span>분양가</span> 8억 2,000 ~ 18억 6,900
              </div>
              <div className="card-adress">서울시 강동구 둔촌동 435-5</div>
              <div className="card-area">70.67㎡ ~ 77.57㎡</div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
};

export default Main;
