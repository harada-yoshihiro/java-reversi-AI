// BEGIN
package j2.review02.s24k1033;

import java.util.ArrayList;
import java.util.Random;

import j2.review02.AI;
import j2.review02.Board;
import j2.review02.Location;

public class MyAI extends AI {

    protected final int depthLimit;
    protected final Random random;
    protected Location result;
    protected int wStone = 10; // 自分の石と相手の石の差分
    protected int wCorner = 1000; // 角
    protected int wNearCorner = 300; // 角の周り3コマ
    protected int wEdge = 75; // 辺
    protected int wMove = 5; // 着手可能な石の数
    protected int wStable = 50; // 安定石 (変更の恐れがない)

    // 色colorのプレイヤーのリバーシAIを生成する．
    // timeLimitedFlagがtrueの場合，時間制限が設定されている．
    public MyAI(int color, boolean timeLimitedFlag) {
        super(color, timeLimitedFlag);
        this.depthLimit = 6; // 探索の深さ
        random = new Random();
    }
    
    // 安定石 (変更の恐れがない石) の数を数える
    // 実装は、隅から続く石の数
    protected int countStable(Board board, int color) {
        int count = 0;
        int[][] corners = {{0,0},{0,7},{7,0},{7,7}};

        for (int[] c : corners) {
            int cx = c[0];
            int cy = c[1];
            int dx = (cx == 0) ? 1 : -1;
            int dy = (cy == 0) ? 1 : -1;

            // 横方向
            for (int x = cx; x >= 0 && x < 8; x += dx) {
                if (board.get(x, cy) == color) count++;
                else break;
            }

            // 縦方向
            for (int y = cy; y >= 0 && y < 8; y += dy) {
                if (board.get(cx, y) == color) count++;
                else break;
            }

            // 斜め方向
            for (int x=cx, y=cy; x>=0 && x<8 && y>=0 && y<8; x+=dx, y+=dy) {
                if (board.get(x, y) == color) count++;
                else break;
            }
        }

        return count;
    }

    // 評価関数
    protected int evaluate(Board board) {
        int score = 0;
        int opp = Board.flip(color);

        int totalDiscs = board.getCount(color) + board.getCount(opp);
        
        // 動的重み付け
        int wStoneDyn, wCornerDyn, wNearCornerDyn, wEdgeDyn, wMoveDyn, wStableDyn;

        if (totalDiscs < 20) { // 序盤
            wStoneDyn = 5;
            wCornerDyn = 1200;
            wNearCornerDyn = 500;
            wEdgeDyn = 100;
            wMoveDyn = 10;
            wStableDyn = 20;
        } else if (totalDiscs < 50) { // 中盤
            wStoneDyn = 10;
            wCornerDyn = 1000;
            wNearCornerDyn = 300;
            wEdgeDyn = 75;
            wMoveDyn = 5;
            wStableDyn = 50;
        } else { // 終盤
            wStoneDyn = 20;
            wCornerDyn = 800;
            wNearCornerDyn = 300;
            wEdgeDyn = 50;
            wMoveDyn = 2;
            wStableDyn = 100;
        }
        // 石数
        score += wStoneDyn * (board.getCount(color) - board.getCount(opp));

        // 角と角周囲
        int[][] corners = {{0,0},{0,7},{7,0},{7,7}};
        for (int[] c : corners) {
            int piece = board.get(c[0], c[1]);
            if (piece == color) score += wCornerDyn;
            else if (piece == opp) score -= wCornerDyn;

            for (int dx=-1; dx<=1; dx++) {
                for (int dy=-1; dy<=1; dy++) {
                    int x = c[0]+dx, y = c[1]+dy;
                    if (x<0 || x>=8 || y<0 || y>=8) continue;
                    if (x==c[0] && y==c[1]) continue;
                    int p = board.get(x,y);
                    if (p==color) score -= wNearCornerDyn;
                    else if (p==opp) score += wNearCornerDyn;
                }
            }
        }

        // 辺
        for (int i=0; i<8; i++) {
            int[] edges = {board.get(i,0), board.get(i,7), board.get(0,i), board.get(7,i)};
            for (int p : edges) {
                if (p==color) score += wEdgeDyn;
                else if (p==opp) score -= wEdgeDyn;
            }
        }

        // 着手可能数
        int myMoves = board.enumerateLegalLocations().size();
        board.pass();
        int oppMoves = board.enumerateLegalLocations().size();
        board.undo();
        score += wMoveDyn * (myMoves - oppMoves);

        // 安定石 (角から続く石)
        score += wStableDyn * (countStable(board, color) - countStable(board, opp));

        return score;
    }

    // マスのリストlocationsの要素をランダムに並べ替える．
    protected void randomizeLocations(ArrayList<Location> locations) {
        var copy = new ArrayList<Location>();
        for (int i = 0; i < locations.size(); i++) {
            copy.add(locations.get(i));
        }
        locations.clear();
        while (!copy.isEmpty()) {
            locations.add(copy.remove(random.nextInt(copy.size())));
        }
    }

    // 最大化関数
    protected int maximize(Board board, int remainingDepth,
                           int alpha, int beta) {

        if (remainingDepth == 0) {
            return evaluate(board);
        }

        var locs = board.enumerateLegalLocations();
        randomizeLocations(locs);

        if (locs.size() == 0) {
            board.pass();

            int score;
            if (board.isLegal()) {
                score = minimize(board, remainingDepth - 1, alpha, beta);
            } else {
                score = evaluate(board);
            }

            board.undo();
            return score;
        }

        int max = Integer.MIN_VALUE;

        for (var loc : locs) {
            board.put(loc);
            int score = minimize(board, remainingDepth - 1, alpha, beta);
            board.undo();

            if (score > max) {
                max = score;

                if (remainingDepth == depthLimit) {
                    result = loc;
                }
            }

            alpha = Math.max(alpha, max);
            if (alpha >= beta) break; // β刈り

         // 5秒制限用の処理
            if (timeLimitedFlag && getTime() > 0.95 * TIME_LIMIT) {
            	break;
            }
        }

        return max;
    }

    // 最小化関数
    protected int minimize(Board board, int remainingDepth,
                           int alpha, int beta) {

        if (remainingDepth == 0) {
            return evaluate(board);
        }

        var locs = board.enumerateLegalLocations();
        randomizeLocations(locs);

        if (locs.size() == 0) {
            board.pass();

            int score;
            if (board.isLegal()) {
                score = maximize(board, remainingDepth - 1, alpha, beta);
            } else {
                score = evaluate(board);
            }

            board.undo();
            return score;
        }

        int min = Integer.MAX_VALUE;

        for (var loc : locs) {
            board.put(loc);
            int score = maximize(board, remainingDepth - 1, alpha, beta);
            board.undo();

            if (score < min) {
                min = score;
            }

            beta = Math.min(beta, min);
            if (beta <= alpha) break; // α刈り
            
         // 5秒制限用の処理
            if (timeLimitedFlag && getTime() > 0.95 * TIME_LIMIT) {
            	break;
            }
        }

        return min;
    }

    // 局面boardに対する手を計算し，石を置くマスの座標を返す．
    // 自分の石の個数が最大になるような手を選ぶ．
    @Override
    public Location compute(Board board) {
        result = null;

        maximize(board, depthLimit,
                 Integer.MIN_VALUE, Integer.MAX_VALUE);

        // 時間切れ用の処理
        if (result == null) {
            var locs = board.enumerateLegalLocations();
            result = locs.get(random.nextInt(locs.size()));
        }

        return result;
    }

}
// END