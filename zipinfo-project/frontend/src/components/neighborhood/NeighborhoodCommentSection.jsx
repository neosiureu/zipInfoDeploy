import { useState, useEffect, useContext } from "react";
import { MemberContext } from "../member/MemberContext";
import { axiosAPI } from "../../api/axiosAPI";

const NeighborhoodCommentSection = ({ boardNo }) => {
  const { member } = useContext(MemberContext);
  const [comments, setComments] = useState([]);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const fetchComments = async () => {
      try {
        setLoading(true);
        const { data } = await axiosAPI.get("/board/boardComment", {
          params: { boardNo },
        });
        setComments(data);
      } catch (error) {
        console.log("댓글 목록 로딩 오류");
      } finally {
        setLoading(false);
      }
    };
    fetchComments();
  }, [refreshKey, boardNo]);
  // 새 댓글등록
  const handleInsertComment = async () => {
    if (!member) return alert("로그인 후 이용해주세요");
    if (!content.trim()) return alert("내용을 작성해주세요");

    // 뭘 서버로 보낼래?
    const params = {
      commentContent: content,
      memberNo: member.memberNo,
      boardNo,
    };

    const data = await axiosAPI.post("/boardComment", params);
    if (Number(data.result) > 0) {
      setContent("");
      setRefreshKey((k) => k + 1);
      // 트리거를 증가시켜 한 화면에서 댓글을 다시 로드한다
    } else {
      alert("댓글 등록 실패");
    }
  };
  const buildTree = (list) => {
    const map = {};
    list.forEach((c) => (map[c.commentNo] = { ...c, children: [] }));
    const roots = [];
    list.forEach((c) => {
      if (c.parentCommentNo) {
        map[c.parentCommentNo]?.children.push(map[c.commentNo]);
      } else {
        roots.push(map[c.commentNo]);
      }
    });
    return roots;
  };

  return (
    <section className="nb-comment-section">
      <h3 className="nb-comment-title">댓글 {comments.length}</h3>

      <div className="nb-comment-form">
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="댓글을 입력하세요"
        />
        <button onClick={handleInsertComment}>등록</button>
      </div>
      {loading ? (
        <p className="nb-comment-loading">로딩 중…</p>
      ) : (
        <CommentTree
          nodes={buildTree(comments)}
          loginMember={member}
          /* 자식에게는 setRefreshKey만 넘겨서 트리거를 직접 증가시킴 */
          reload={() => setRefreshKey((k) => k + 1)}
        />
      )}
    </section>
  );
};

export default NeighborhoodCommentSection;
