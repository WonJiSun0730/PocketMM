package com.example.projectmm.domain.state;

// AI는 정의 식별자와 변화량만 제안한다. 이전 값이나 목표 확정값을 소유하지 않는다.
public record CustomStateChangeProposal(Long definitionId, Double delta) {
    public void validate() {
        if (definitionId == null || definitionId <= 0 || delta == null || !Double.isFinite(delta)) {
            throw new IllegalArgumentException("Proposal requires a positive definition ID and a finite delta.");
        }
    }

    public double resolve(double currentValue, double minValue, double maxValue) {
        validate();
        double result = currentValue + delta;
        // 유한한 두 숫자의 합도 overflow될 수 있다. 범위 초과는 보정하지 않고 거부한다.
        if (!Double.isFinite(currentValue) || !Double.isFinite(minValue) || !Double.isFinite(maxValue)
                || minValue > maxValue || !Double.isFinite(result) || result < minValue || result > maxValue) {
            throw new IllegalArgumentException("Proposed result must be finite and within the definition range.");
        }
        return result;
    }
}
