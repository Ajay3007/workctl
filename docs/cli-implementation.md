# CLI EVOLUTION

## Smart Weighted Model

### 1️⃣ Completion Strength (Weight: 30%)

How much of your backlog is completed?

```java
completionRate = done / total
scoreCompletion = completionRate * 100
```

Weight: 30

### 2️⃣ Weekly Velocity (Weight: 25%)

Recent execution power.

```java
velocityScore = min( completedThisWeek * 10 , 100 )
```

So:

- 5 tasks/week → 50

- 10 tasks/week → capped at 100

Weight: 25

### 3️⃣ Focus Score (Weight: 15%)

Too many open tasks = diluted focus.

Ideal open range: 3–7

```java
if open <= 7 → 100
if open <= 15 → 70
else → 40
```


Weight: 15

### 4️⃣ Stagnation Penalty (Weight: 20%)

Old open tasks reduce score.

```java
penalty = stagnantCount * 5
stagnationScore = max(100 - penalty, 0)
```

Weight: 20

### 5️⃣ Consistency Bonus (Weight: 10%)

If tasks completed every week for last 3 weeks → +10

Otherwise 0–5.

Weight: 10

### 🧮 Final Score Formula

```java
double finalScore =
completionScore * 0.30 +
velocityScore * 0.25 +
focusScore * 0.15 +
stagnationScore * 0.20 +
consistencyScore * 0.10;
```

### 📊 Interpretation Layer

Add qualitative meaning:

Score	Status

85+	🔥 Elite Execution

70–85	🚀 Strong Momentum

50–70	⚖ Stable but Improve

30–50	⚠ Fragmented

<30	🧊 Stalled



